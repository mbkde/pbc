FROM java:8
RUN mkdir -p /service && wget --output-document=/service/unicreds.tgz https://github.com/Versent/unicreds/releases/download/v1.5.0/unicreds_1.5.0_linux_x86_64.tgz \
  && cd /service && tar --owner=root --group=root -zxvf /service/unicreds.tgz \
  && chmod +x /service/unicreds \
  && rm -rf /service/unicreds.tgz
ADD files/entrypoint.sh /service/entrypoint.sh
RUN chmod a+x /service/entrypoint.sh
ADD target/ecs-scheduler-service-*.jar /service/EcsSchedulerService.jar
EXPOSE 8080
ENTRYPOINT ["/service/entrypoint.sh"]

