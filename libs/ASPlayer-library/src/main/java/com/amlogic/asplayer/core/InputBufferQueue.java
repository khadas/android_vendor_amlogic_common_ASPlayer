package com.amlogic.asplayer.core;

public interface InputBufferQueue {

    boolean isFull();
    boolean isEmpty();

    void clear();

    boolean pop(InputBuffer inputBuffer);

    long getSizeInUs();
    int getSize();
}
