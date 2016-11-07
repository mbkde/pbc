package com.atlassian.buildeng.ecs.api;

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationPersistence;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;


public class Scheduler {

    //silly BUT we already (de)serialize Configuration this way
    public static Scheduler fromJson(String v) {
        JsonParser p = new JsonParser();
        JsonElement obj = p.parse(v);
        if (obj.isJsonObject()) {
            JsonObject oo = obj.getAsJsonObject();
            JsonPrimitive uuid = oo.getAsJsonPrimitive("uuid");
            JsonPrimitive resultId = oo.getAsJsonPrimitive("resultId");
            JsonPrimitive server = oo.getAsJsonPrimitive("bambooServer");
            JsonPrimitive sidekick = oo.getAsJsonPrimitive("sidekick");
            JsonObject conf = oo.getAsJsonObject("configuration");
            if (uuid == null || resultId == null || conf == null || server == null || sidekick == null) {
                throw new IllegalArgumentException("Wrong format!");
            }
            Configuration c = ConfigurationPersistence.toConfiguration(conf.toString());
            if (c == null) {
                throw new IllegalArgumentException("Wrong format!");
            }
            Scheduler scheduler = new Scheduler(uuid.getAsString(), resultId.getAsString(), server.getAsString(), sidekick.getAsString(), c);

            JsonPrimitive taskArn = oo.getAsJsonPrimitive("taskARN");
            if (taskArn != null) {
                scheduler.setTaskARN(taskArn.getAsString());
            }
            return scheduler;
        }
        throw new IllegalArgumentException("Wrong format!");
    }
    private final String uuid;
    private final String resultId;
    private final String bambooServer;
    private final String sidekick;
    private String taskARN;
    private final Configuration configuration;

    public Scheduler(String uuid, String resultId, String server, String sidekick, Configuration configuration) {
        this.uuid = uuid;
        this.resultId = resultId;
        this.configuration = configuration;
        this.bambooServer = server;
        this.sidekick = sidekick;
    }

    public String getUuid() {
        return uuid;
    }

    public String getResultId() {
        return resultId;
    }

    public String getBambooServer() {
        return bambooServer;
    }

    public String getSidekick() {
        return sidekick;
    }


    public Configuration getConfiguration() {
        return configuration;
    }

    public String getTaskARN() {
        return taskARN;
    }

    public void setTaskARN(String taskARN) {
        this.taskARN = taskARN;
    }

}
