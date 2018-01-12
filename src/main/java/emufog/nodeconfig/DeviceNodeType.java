package emufog.nodeconfig;

public class DeviceNodeType extends NodeType{

    private int scalingFactor;
    private float averageDeviceCount;


    public DeviceNodeType(int memoryLimit, int cpuShare, int scalingFactor, float averageDeviceCount) {
        super(memoryLimit, cpuShare);
        this.scalingFactor = scalingFactor;
        this.averageDeviceCount = averageDeviceCount;
    }

    public int getScalingFactor() {
        return scalingFactor;
    }

    public float getAverageDeviceCount() {
        return averageDeviceCount;
    }
}