# TODO: test fetching spec file from koji, get correct provider for packit

import os

import pytest
from unittest.mock import patch, MagicMock

import responses
from copr.v3 import BuildProxy, BuildChrootProxy
from koji import GenericError

from src.exceptions import FetchError
from src.fetcher import CoprProvider, KojiProvider, URLProvider
from tests.spells import mock_multiple_responses, sort_by_name


class TestProviders:
    koji_url = "https://koji.fedoraproject.org"

    @pytest.mark.parametrize(
        "chroot, baseurl",
        [
            pytest.param("fedora-39_x86_64", "https://www.XYZ.uwu/"),
            pytest.param("srpm-builds", "https://www.XYZ.uwu/"),
            pytest.param("fedora-39_x86_64", None),
            pytest.param("srpm-builds", None),
        ],
    )
    @responses.activate
    @patch.object(BuildProxy, "get")
    @patch.object(BuildChrootProxy, "get")
    def test_fetch_copr_logs(
        self,
        mock_build_chroot_proxy,
        mock_build_proxy,
        chroot,
        baseurl,
        copr_chroot_logs,
        copr_srpm_logs,
    ):
        mock_build_chroot_proxy.return_value = MagicMock(result_url=baseurl)
        mock_build_proxy.return_value = MagicMock(source_package={"url": baseurl})

        logs = copr_srpm_logs if chroot == "srpm-builds" else copr_chroot_logs
        if baseurl:
            if chroot == "srpm-builds":
                baseurl = os.path.dirname(baseurl)

            mock_multiple_responses(baseurl, logs)

        provider = CoprProvider(123, chroot)
        if not baseurl:
            with pytest.raises(FetchError):
                provider.fetch_logs()
            return

        expected_result = sorted(
            [
                {"name": name.removesuffix(".gz"), "content": content}
                for name, content in logs.items()
            ],
            key=sort_by_name,
        )
        assert expected_result == sorted(provider.fetch_logs(), key=sort_by_name)

    @pytest.mark.parametrize(
        "chroot, baseurl",
        [
            pytest.param("fedora-39_x86_64", "https://www.XYZ.uwu/"),
            pytest.param("srpm-builds", "https://www.XYZ.uwu/"),
        ],
    )
    @responses.activate
    @patch.object(BuildProxy, "get")
    @patch.object(BuildChrootProxy, "get")
    def test_fetch_copr_spec(
        self, mock_build_chroot_proxy, mock_build_proxy, chroot, baseurl, fake_spec_file
    ):
        mock_build_chroot_proxy.return_value = MagicMock(result_url=baseurl)
        projectname = "pikachu"
        mock_build_proxy.return_value = MagicMock(
            source_package={"name": projectname, "url": baseurl}
        )

        if chroot == "srpm-builds":
            baseurl = os.path.dirname(baseurl)

        spec_name = f"{projectname}.spec"
        responses.add(
            responses.GET, url=f"{baseurl}/{spec_name}", body=fake_spec_file, status=200
        )

        assert {"name": spec_name, "content": fake_spec_file} == CoprProvider(
            123, chroot
        ).fetch_spec_file()

    @pytest.mark.parametrize(
        "arch",
        [
            pytest.param("x86_64"),
            pytest.param("noarch"),
        ],
    )
    @responses.activate
    @patch("koji.ClientSession")
    def test_fetch_koji_logs_from_build(
        self,
        mock_koji_client,
        arch,
        koji_chroot_logs_x86_64,
        koji_chroot_logs_response_x86_64,
    ):
        mock_koji_client.return_value = MagicMock(
            getBuild=lambda _: {"some_build": 123},
            getBuildLogs=lambda _: koji_chroot_logs_response_x86_64,
        )
        mock_multiple_responses(self.koji_url, koji_chroot_logs_x86_64)

        provider = KojiProvider(123, arch)
        if arch != "x86_64":
            with pytest.raises(FetchError):
                provider.fetch_logs()
            return

        result = sorted(provider.fetch_logs(), key=sort_by_name)
        expected_result = sorted([
            {"name": name, "content": content}
            for name, content in koji_chroot_logs_x86_64.items()
        ], key=sort_by_name)

        assert len(result) == 3
        assert expected_result == result

    @responses.activate
    @patch("koji.ClientSession")
    @patch.object(
        KojiProvider,
        "_fetch_task_logs_from_koji_cli",
        return_value=[{"name": "content"}],
    )
    def test_fetch_koji_logs_from_task(
        self, mock_cli_fetch, mock_koji_client, koji_chroot_logs_response_x86_64
    ):
        mock_koji_client.return_value = MagicMock(
            getBuild=MagicMock(side_effect=GenericError()),
            getBuildLogs=lambda _: koji_chroot_logs_response_x86_64,
        )

        KojiProvider(123, "arch").fetch_logs()
        # it just works, I am not gonna test it since it'll be replaced by calling api
        mock_cli_fetch.assert_called_once()

    @responses.activate
    def test_fetch_url_logs(self):
        url = "https://www.fake.lol"
        provider = URLProvider(url)
        responses.add(responses.GET, url=url, body="text")
        assert provider.fetch_logs() == [{"name": "Log file", "content": "text"}]

    def test_fetch_url_spec(self):
        assert {
            "name": "fake_spec_name.spec",
            "content": "fake spec file",
        } == URLProvider("https://www.fake.lol").fetch_spec_file()
