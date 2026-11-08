package xyz.daikirai.music;

public interface AudioOutput {
    void write(byte[] data, int offset, int length);

    void setGain(float gain);

    boolean hasGain();

    void flush();

    void drain();

    void close();

    boolean isLost();
}
