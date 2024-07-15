package com.nulink.livingratio.utils;

import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.service.CreateNodePoolEventService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NodePoolMapSingleton {

    @Resource
    private CreateNodePoolEventService createNodePoolEventService;

    private static NodePoolMapSingleton instance;

    private Map<String, CreateNodePoolEvent> sharedMap;

    public NodePoolMapSingleton() {
        this.sharedMap = new ConcurrentHashMap<>();
    }

    public static synchronized NodePoolMapSingleton getInstance() {
        if (instance == null) {
            instance = new NodePoolMapSingleton();
        }
        return instance;
    }

    public static void put(String key, CreateNodePoolEvent value) {
        NodePoolMapSingleton.getInstance().sharedMap.put(key, value);
    }

    public static CreateNodePoolEvent get(String key) {
        return NodePoolMapSingleton.getInstance().sharedMap.get(key);
    }

    public static boolean containsKey(String key) {
        return NodePoolMapSingleton.getInstance().sharedMap.containsKey(key);
    }

    public static void remove(String key) {
        NodePoolMapSingleton.getInstance().sharedMap.remove(key);
    }

    public static void clear() {
        NodePoolMapSingleton.getInstance().sharedMap.clear();
    }

    public static Map<String, CreateNodePoolEvent> getSharedMap() {
        return NodePoolMapSingleton.getInstance().sharedMap;
    }

    @PostConstruct
    private void initShareMap() {
        if (sharedMap == null) {
            sharedMap = new ConcurrentHashMap<>();
        }
        List<CreateNodePoolEvent> nodePoolEvents = createNodePoolEventService.findAll();
        nodePoolEvents.forEach(nodePoolEvent -> sharedMap.put(nodePoolEvent.getNodePoolAddress(), nodePoolEvent));
    }
}
