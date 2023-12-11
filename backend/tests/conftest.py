import pytest


FAKE_SPEC_FILE = """
Name:           test-package
Version:        0.1.0
Release:        1%{?dist}
Summary:        Fake test package

License:        GPLv3
URL:            https://example.com/%{name}
Source0:        %{url}/%{version}.tar.gz


%description
%{summary}


%changelog
* Fri May 12 2023 user@not-google.com
- initial release
"""


@pytest.fixture()
def fake_spec_file():
    yield FAKE_SPEC_FILE


@pytest.fixture()
def copr_chroot_logs():
    prefix = "./backend/tests/files/copr"
    with (
        open(f"{prefix}/chroot_build.log.gz") as f_build_log,
        open(f"{prefix}/chroot_backend.log.gz") as f_backend_log,
        open(f"{prefix}/chroot_builder-live.log.gz") as f_builder_live_log,
    ):
        yield {
            "build.log.gz": f_build_log.read(),
            "backend.log.gz": f_backend_log.read(),
            "builder-live.log.gz": f_builder_live_log.read(),
        }


@pytest.fixture()
def copr_srpm_logs():
    prefix = "./backend/tests/files/copr"
    with (
        open(f"{prefix}/srpm_backend.log.gz") as f_backend_log,
        open(f"{prefix}/srpm_builder-live.log.gz") as f_builder_live_log,
    ):
        yield {
            "backend.log.gz": f_backend_log.read(),
            "builder-live.log.gz": f_builder_live_log.read(),
        }


@pytest.fixture()
def koji_chroot_logs_x86_64():
    prefix = "./backend/tests/files/koji"
    with (
        open(f"{prefix}/build_x86_64.log") as f_build_log,
        open(f"{prefix}/mock_output_x86_64.log") as f_mock_output_log,
        open(f"{prefix}/root_x86_64.log") as f_root_log,
    ):
        yield {
            "build.log": f_build_log.read(),
            "mock_output.log": f_mock_output_log.read(),
            "root.log": f_root_log.read(),
        }


@pytest.fixture()
def koji_chroot_logs_response_x86_64():
    yield [
        {
            "dir": "x86_64",
            "name": "state.log",
            "path": None,
        },
        {
            "dir": "uwu",
            "name": "build.log",
            "path": None,
        },
        {
            "dir": "x86_64",
            "name": "build.log",
            "path": "build.log",
        },
        {
            "dir": "x86_64",
            "name": "mock_output.log",
            "path": "mock_output.log",
        },
        {
            "dir": "x86_64",
            "name": "root.log",
            "path": "root.log",
        },
    ]


@pytest.fixture()
def packit_koji_chroot_logs():
    pass


@pytest.fixture()
def packit_copr_chroot_logs():
    pass


@pytest.fixture()
def packit_copr_srpm_logs():
    pass


@pytest.fixture()
def url_logs():
    pass
