FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# same-origin paths; nginx proxies them to api/gateway
ENV VITE_API_BASE=/api
ENV VITE_WS_URL=/ws
ARG VITE_API_KEY=dev-key
ENV VITE_API_KEY=$VITE_API_KEY
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
