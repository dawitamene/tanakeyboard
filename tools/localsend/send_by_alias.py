#!/usr/bin/env python3

import argparse
import concurrent.futures
import http.client
import importlib.machinery
import importlib.util
import ipaddress
import json
import re
import ssl
import subprocess
import sys
from pathlib import Path


DEFAULT_PORT = 54000
INFO_PATHS = ("/api/localsend/v2/info", "/api/localsend/v1/info")
REGISTER_PATH = "/api/localsend/v2/register"
REGISTER_BODY = {
    "alias": "Addiyon Build",
    "version": "2.0",
    "deviceModel": "macOS",
    "deviceType": "headless",
    "fingerprint": "addiyon-keyboard-localsend-scanner",
    "port": DEFAULT_PORT,
    "protocol": "https",
    "download": False,
}


def run_text(command):
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def default_interface():
    output = run_text(["/sbin/route", "-n", "get", "default"])
    match = re.search(r"^\s*interface:\s+(\S+)", output, re.MULTILINE)
    if not match:
        raise RuntimeError("Could not determine the default network interface.")
    return match.group(1)


def default_gateway():
    output = run_text(["/sbin/route", "-n", "get", "default"])
    match = re.search(r"^\s*gateway:\s+(\d+(?:\.\d+){3})", output, re.MULTILINE)
    if not match:
        raise RuntimeError("Could not determine the default network gateway.")
    return ipaddress.IPv4Address(match.group(1))


def network_for_interface(interface):
    output = run_text(["/sbin/ifconfig", interface])
    match = re.search(
        r"^\s*inet\s+(\d+(?:\.\d+){3})\s+netmask\s+(0x[0-9a-fA-F]+|\d+(?:\.\d+){3})",
        output,
        re.MULTILINE,
    )
    if not match:
        raise RuntimeError(f"Could not determine the IPv4 network for {interface}.")
    address = ipaddress.IPv4Address(match.group(1))
    raw_mask = match.group(2)
    netmask = str(ipaddress.IPv4Address(int(raw_mask, 16))) if raw_mask.startswith("0x") else raw_mask
    network = ipaddress.IPv4Network(f"{address}/{netmask}", strict=False)
    if network.num_addresses > 256:
        network = ipaddress.IPv4Network(f"{address}/24", strict=False)
    return address, network


def request_info(address, port, scheme, path, timeout, method="GET", request_body=None):
    if scheme == "https":
        context = ssl.create_default_context()
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        connection = http.client.HTTPSConnection(str(address), port, timeout=timeout, context=context)
    else:
        connection = http.client.HTTPConnection(str(address), port, timeout=timeout)
    try:
        body = json.dumps(request_body).encode() if request_body is not None else None
        headers = {"Content-Type": "application/json"} if body is not None else {}
        connection.request(method, path, body=body, headers=headers)
        response = connection.getresponse()
        body = response.read()
        if response.status != 200:
            return None
        info = json.loads(body)
        if not isinstance(info, dict) or not info.get("alias"):
            return None
        return info
    finally:
        connection.close()


def probe(address, port, timeout):
    for scheme in ("https", "http"):
        try:
            info = request_info(
                address,
                port,
                scheme,
                REGISTER_PATH,
                timeout,
                method="POST",
                request_body=REGISTER_BODY,
            )
            if info:
                return address, info
        except (OSError, ssl.SSLError, ValueError, json.JSONDecodeError, http.client.HTTPException):
            pass
        for path in INFO_PATHS:
            try:
                info = request_info(address, port, scheme, path, timeout)
                if info:
                    return address, info
            except (OSError, ssl.SSLError, ValueError, json.JSONDecodeError, http.client.HTTPException):
                pass
    return None


def discover_by_alias(
    alias,
    local_address,
    network,
    port,
    gateway=None,
    scan_subnet=True,
    timeout=1.5,
    workers=24,
):
    if gateway is not None and gateway != local_address:
        result = probe(gateway, port, 8)
        if result and alias.casefold() in str(result[1].get("alias", "")).casefold():
            return result
        if not scan_subnet:
            raise RuntimeError(
                f"The hotspot gateway did not answer as LocalSend device '{alias}' on port {port}. "
                "Restart LocalSend on the phone and retry."
            )
    addresses = [address for address in network.hosts() if address != local_address]
    if gateway in addresses:
        addresses.remove(gateway)
    matches = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(workers, len(addresses))) as executor:
        futures = [executor.submit(probe, address, port, timeout) for address in addresses]
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            if result and alias.casefold() in str(result[1].get("alias", "")).casefold():
                matches.append(result)
    if not matches:
        raise RuntimeError(
            f"No LocalSend device matching '{alias}' was found on {network}. "
            "Open LocalSend on the phone and retry."
        )
    if len(matches) > 1:
        names = ", ".join(sorted(str(info.get("alias")) for _, info in matches))
        raise RuntimeError(f"Multiple LocalSend devices matched '{alias}': {names}")
    return matches[0]


def send_with_cli(cli, address, port, source):
    loader = importlib.machinery.SourceFileLoader("addiyon_localsend_cli", str(cli))
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    module.PORT = port
    arguments = argparse.Namespace(
        alias="Addiyon Build",
        files=[str(source)],
        ip=str(address),
        to=None,
    )
    try:
        module.cmd_send(arguments)
    except SystemExit as error:
        return int(error.code or 0)
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cli", required=True)
    parser.add_argument("--alias", required=True)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--scan-subnet", action="store_true")
    parser.add_argument("--discover-only", action="store_true")
    parser.add_argument("file", nargs="?")
    args = parser.parse_args()

    cli = Path(args.cli).expanduser().resolve()
    if not cli.is_file():
        raise RuntimeError(f"LocalSend CLI was not found at {cli}.")
    source = Path(args.file).expanduser().resolve() if args.file else None
    if not args.discover_only and source is None:
        raise RuntimeError("A file is required unless --discover-only is used.")
    if source is not None and not source.is_file():
        raise RuntimeError(f"File to send was not found at {source}.")

    interface = default_interface()
    gateway = default_gateway()
    local_address, network = network_for_interface(interface)
    print(f"Finding LocalSend device '{args.alias}' on the current network...")
    address, info = discover_by_alias(
        args.alias,
        local_address,
        network,
        args.port,
        gateway=gateway,
        scan_subnet=args.scan_subnet,
    )
    if args.discover_only:
        print(f"Found {info['alias']}.")
        return 0
    print(f"Found {info['alias']}. Sending {source.name}...")
    return send_with_cli(cli, address, args.port, source)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
