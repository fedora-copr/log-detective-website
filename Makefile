IMAGE_NAME=log-detective-website_test-backend
CONTAINER_ENGINE ?= $(shell command -v podman 2> /dev/null || echo docker)
ENV=devel
FEEDBACK_DIR=/persistent/results
PYTHONPATH=/opt/log-detective-website/backend


build-image:
	$(CONTAINER_ENGINE) build --rm --tag $(IMAGE_NAME) -f docker/backend/Dockerfile -f backend/tests/Dockerfile


test-backend-in-container:
	$(CONTAINER_ENGINE) build --rm --tag $(IMAGE_NAME) \
		-f docker/backend/Dockerfile \
		-f backend/tests/Dockerfile \
	$(CONTAINER_ENGINE) run -ti $(IMAGE_NAME) \
		--env PYTHONPATH \
		--env FEEDBACK_DIR \
		--env ENV \
		-v .:/opt/log-detective-website:z \
		bash -c "pytest -vvv opt/log-detective-website/backend/tests/" \
