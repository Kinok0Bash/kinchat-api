FROM bellsoft/liberica-runtime-container:jre-25-glibc
COPY ./build/libs/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
