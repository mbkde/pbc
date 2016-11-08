FROM java:8
ADD target/ecs-scheduler-service-*.jar /service/EcsSchedulerService.jar

EXPOSE 8080

CMD ["java", "-jar", "/service/EcsSchedulerService.jar"]
