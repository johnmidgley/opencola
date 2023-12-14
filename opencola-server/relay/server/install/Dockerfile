FROM openjdk:17
COPY ./oc-relay/ /oc-relay
COPY ./opencola-relay.yaml /oc-relay
WORKDIR /oc-relay
EXPOSE 2652
CMD ["sh", "./bin/server"]
