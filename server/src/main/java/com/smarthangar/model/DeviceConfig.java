package com.smarthangar.model;

public class DeviceConfig {

    private Long id;
    private String deviceName;
    private Long aircraftId;
    private String mode;

    public DeviceConfig() {
    }

    public DeviceConfig(
            Long id,
            String deviceName,
            Long aircraftId,
            String mode) {

        this.id = id;
        this.deviceName = deviceName;
        this.aircraftId = aircraftId;
        this.mode = mode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Long getAircraftId() {
        return aircraftId;
    }

    public void setAircraftId(Long aircraftId) {
        this.aircraftId = aircraftId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
