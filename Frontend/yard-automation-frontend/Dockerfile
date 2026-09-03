# Stage 1: Build
FROM node:20 AS build
ARG CI_JOB_TOKEN
ARG CI_API_V4_URL
ARG TOKEN_TYPE="Job-Token"

WORKDIR /app

# Copy dependency files, including the repo's own .npmrc (registry mappings + strict-ssl=false
# for the internal CA). The Dockerfile previously never copied this file and instead generated
# a bare .npmrc from scratch, silently losing that config.
COPY Frontend/yard-automation-frontend/package*.json Frontend/yard-automation-frontend/.npmrc ./

# package-lock.json's "resolved" URLs point at the internal Nexus npm proxy and the private
# GitLab package registry (baked in from whoever generated the lockfile on the corporate
# network). Both serve certs signed by an internal CA that node:20 doesn't trust, so npm ci
# fails every fetch with UNABLE_TO_VERIFY_LEAF_SIGNATURE / UNABLE_TO_GET_ISSUER_CERT_LOCALLY -
# which is what was actually causing the "Exit handler never called!" crashes all along.
# NODE_EXTRA_CA_CERTS adds this CA on top of Node's built-in trust store (unlike npm's own
# "cafile" config, which would replace it and break the public registry).
COPY Frontend/yard-automation-frontend/frauscher-ca.crt /usr/local/share/ca-certificates/frauscher-ca.crt
ENV NODE_EXTRA_CA_CERTS=/usr/local/share/ca-certificates/frauscher-ca.crt

# Append CI auth on top of the repo's checked-in .npmrc. The resolved tarball URLs baked into
# package-lock.json point at /api/v4/projects/<id>/packages/npm/..., not /api/v4/packages/npm/...,
# so an authToken scoped to the latter path never matches and the request goes out
# unauthenticated (GitLab then returns 404 "Project not found" for the private project).
# Scoping the token at the host root instead makes it match any project path on this host.
# Retry once and dump the actual npm debug log on final failure so we can see the real
# underlying error instead of just the generic wrapper.
RUN CI_HOST="${CI_API_V4_URL#http*://}" \
  && CI_HOST="${CI_HOST%%/*}" \
  && echo "update-notifier=false" >> .npmrc \
  && echo "always-auth=true" >> .npmrc \
  && echo "//${CI_HOST}/:_authToken=${CI_JOB_TOKEN}" >> .npmrc \
  && npm install -g npm@10.9.3 \
  && (npm ci --no-audit --no-fund || (rm -rf node_modules && npm ci --no-audit --no-fund)) \
  && test -x node_modules/.bin/ng \
  && rm -f .npmrc \
  || (cat /root/.npm/_logs/*-debug-0.log 2>/dev/null; exit 1)

# Copy source and build
COPY Frontend/yard-automation-frontend/ ./
RUN ./node_modules/.bin/ng build --configuration production

# Stage 2: Serve
FROM nginx:alpine
COPY --from=build /app/dist/yard-automation-frontend/browser /usr/share/nginx/html
COPY Frontend/yard-automation-frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
