# syntax=docker/dockerfile:1.7
# Vite inlines VITE_* at build time, so Keycloak's coordinates are build arguments — same pattern
# as admin/Dockerfile. Unlike admin, VITE_API_TARGET is not one of them: the app only ever calls
# the relative path /api/v1/..., which this image's nginx proxies onward at runtime (GATEWAY_ORIGIN
# below), so which gateway it talks to is a deploy-time choice, not a build-time one.
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_KEYCLOAK_URL=https://msbs.io.vn/auth
ARG VITE_KEYCLOAK_REALM=msb
ARG VITE_KEYCLOAK_CLIENT_ID=msb-web
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /src/dist /usr/share/nginx/html
COPY nginx-templates/default.conf.template /etc/nginx/templates/default.conf.template
# Where nginx forwards /api/** to at container start (the official nginx image's own
# envsubst-on-templates entrypoint fills ${GATEWAY_ORIGIN} into the template below).
# NGINX_ENVSUBST_FILTER restricts that substitution to this one variable — left unset, envsubst
# would also try to expand nginx's own $host/$remote_addr/... in the template and blank them out.
ENV GATEWAY_ORIGIN=https://msbs.io.vn \
    GATEWAY_HOST=msbs.io.vn \
    NGINX_ENVSUBST_FILTER="GATEWAY_ORIGIN|GATEWAY_HOST"
# The platform's one hard requirement (runtime-contract.md): listen on 8080.
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/health >/dev/null 2>&1 || exit 1
