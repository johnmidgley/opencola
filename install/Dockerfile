## Creates an openjdk image that runs oc
# docker build -t oc .
# docker run -d -p 5795:5795 -it oc
FROM openjdk:16
COPY ./opencola/ /opencola
WORKDIR /opencola/server
CMD ["sh", "bin/opencola-server"]
EXPOSE 5795
