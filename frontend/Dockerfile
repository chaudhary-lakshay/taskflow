# Build: Node 24. Serve: unprivileged nginx (runs as the non-root user nginx, port 8080), which also forwards /api to the backend, so the browser sees one origin.
FROM node:24.22.3-alpine3.24 AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.7-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
