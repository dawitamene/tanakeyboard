import ipaddress
import unittest
from unittest import mock

import send_by_alias


class SendByAliasTest(unittest.TestCase):
    @mock.patch.object(send_by_alias, "run_text")
    def test_reads_hex_netmask_from_default_interface(self, run_text):
        run_text.return_value = "inet 10.205.30.124 netmask 0xffffff00 broadcast 10.205.30.255\n"

        address, network = send_by_alias.network_for_interface("en0")

        self.assertEqual(ipaddress.IPv4Address("10.205.30.124"), address)
        self.assertEqual(ipaddress.IPv4Network("10.205.30.0/24"), network)

    @mock.patch.object(send_by_alias, "run_text")
    def test_limits_large_network_to_local_24(self, run_text):
        run_text.return_value = "inet 10.20.30.40 netmask 0xffff0000 broadcast 10.20.255.255\n"

        _, network = send_by_alias.network_for_interface("en0")

        self.assertEqual(ipaddress.IPv4Network("10.20.30.0/24"), network)

    @mock.patch.object(send_by_alias, "probe")
    def test_matches_alias_case_insensitively(self, probe):
        def result(address, _port, _timeout):
            if str(address) == "192.168.1.3":
                return address, {"alias": "Samsung"}
            return None

        probe.side_effect = result

        address, info = send_by_alias.discover_by_alias(
            "samsung",
            ipaddress.IPv4Address("192.168.1.2"),
            ipaddress.IPv4Network("192.168.1.0/29"),
            54000,
        )

        self.assertEqual(ipaddress.IPv4Address("192.168.1.3"), address)
        self.assertEqual("Samsung", info["alias"])

    @mock.patch.object(send_by_alias, "request_info")
    def test_probe_uses_register_discovery_first(self, request_info):
        request_info.return_value = {"alias": "Samsung"}

        result = send_by_alias.probe(ipaddress.IPv4Address("192.168.1.3"), 54000, 0.45)

        self.assertEqual("Samsung", result[1]["alias"])
        request_info.assert_called_once_with(
            ipaddress.IPv4Address("192.168.1.3"),
            54000,
            "https",
            send_by_alias.REGISTER_PATH,
            0.45,
            method="POST",
            request_body=send_by_alias.REGISTER_BODY,
        )

    @mock.patch.object(send_by_alias, "probe")
    def test_checks_dynamic_gateway_before_scanning_subnet(self, probe):
        gateway = ipaddress.IPv4Address("192.168.1.1")
        probe.return_value = gateway, {"alias": "Samsung"}

        result = send_by_alias.discover_by_alias(
            "Samsung",
            ipaddress.IPv4Address("192.168.1.2"),
            ipaddress.IPv4Network("192.168.1.0/24"),
            54000,
            gateway=gateway,
        )

        self.assertEqual(gateway, result[0])
        probe.assert_called_once_with(gateway, 54000, 8)

    @mock.patch.object(send_by_alias, "probe", return_value=None)
    def test_hotspot_mode_fails_without_scanning_after_gateway(self, probe):
        gateway = ipaddress.IPv4Address("192.168.1.1")

        with self.assertRaisesRegex(RuntimeError, "hotspot gateway"):
            send_by_alias.discover_by_alias(
                "Samsung",
                ipaddress.IPv4Address("192.168.1.2"),
                ipaddress.IPv4Network("192.168.1.0/24"),
                54000,
                gateway=gateway,
                scan_subnet=False,
            )

        probe.assert_called_once_with(gateway, 54000, 8)


if __name__ == "__main__":
    unittest.main()
