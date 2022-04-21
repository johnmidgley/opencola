## Creates an openjdk image that runs oc
# Build: "docker build -t oc ."
# Run on windows: "docker run -d -p 5795:5795 -v %cd%\opencola\storage:/opencola/storage -it oc"
FROM openjdk:16
COPY ./opencola/ /opencola
WORKDIR /opencola/server
CMD ["sh", "bin/opencola-server"]
EXPOSE 5795
