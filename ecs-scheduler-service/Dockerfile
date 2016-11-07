FROM java:8
ADD target/ecs-scheduler-service-*-SNAPSHOT.jar /service/EcsSchedulerService.jar

EXPOSE 8080

CMD ["java", "-jar", "/service/EcsSchedulerService.jar"]
