## Creates an openjdk image that runs oc
# Build: "docker build -t oc ."
# Run on windows: docker run -d -p 5795:5795 -v %appdata%\opencola\:/opencola/storage -it oc
# Run on MacOS: [TBD]
# Run on Linux: [TBD]

FROM openjdk:17
COPY ./opencola/ /opencola
WORKDIR /opencola/server
CMD ["sh", "bin/opencola-server"]
EXPOSE 5795
