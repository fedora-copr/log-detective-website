FROM registry.fedoraproject.org/fedora:38
MAINTAINER copr-devel@lists.fedorahosted.org

RUN dnf -y update && \
    # Base packages
    dnf -y install htop \
                   make \
                   wget \
                   net-tools \
                   iputils \
                   vim \
                   mlocate \
                   git \
                   sudo \
                   python3-ipdb \
                   findutils \
    # Frontend
    && dnf -y install \
                   npm \
                   java \
    # Backend
    && dnf -y install \
                   python3-flask \
                   python3-requests \
                   python3-koji \
                   python3-copr \
    && dnf clean all


RUN git clone https://github.com/fedora-copr/lightspeed-build-website.git /src


WORKDIR /src/frontend
RUN npm install
RUN npm install -g shadow-cljs
RUN npx shadow-cljs release app


# TODO We need to use an actual httpd server
WORKDIR /src/backend
CMD flask --app main run --host 0.0.0.0 --port 80