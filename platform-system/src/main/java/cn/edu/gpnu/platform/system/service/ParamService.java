package cn.edu.gpnu.platform.system.service;

public interface ParamService {

    int getInt(String key, int defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    String getString(String key, String defaultValue);
}
