FROM node:12-alpine AS BUILD_IMAGE
WORKDIR /usr/src/app
COPY package.json yarn.lock ./
RUN yarn --frozen-lockfile
COPY . .
RUN yarn install

FROM node:12-alpine
WORKDIR /usr/src/app
# copy from build image
COPY --from=BUILD_IMAGE /usr/src/app/index.js ./
COPY --from=BUILD_IMAGE /usr/src/app/node_modules ./node_modules
EXPOSE 3000
ENTRYPOINT ["node", "./index.js"]