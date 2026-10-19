FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Réseau lent ou instable : cache npm conservé entre les builds, réessais et timeouts longs.
RUN --mount=type=cache,id=smartrecruit-npm,target=/root/.npm \
    npm ci --fetch-retries=5 --fetch-retry-mintimeout=5000 --fetch-timeout=120000
COPY . .
ARG VITE_USE_MOCKS=false
ENV VITE_USE_MOCKS=$VITE_USE_MOCKS
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
