/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

import java.util.Objects;

public class AudioLang {

    private int mFirstLang;

    private int mSecondLang;

    public AudioLang() {
        this.mFirstLang = 0;
        this.mSecondLang = 0;
    }

    public AudioLang(int firstLang, int secondLang) {
        this.mFirstLang = firstLang;
        this.mSecondLang = secondLang;
    }

    public int getFirstLanguage() {
        return mFirstLang;
    }

    public void setFirstLanguage(int firstLanguage) {
        this.mFirstLang = firstLanguage;
    }

    public int getSecondLanguage() {
        return mSecondLang;
    }

    public void setSecondLanguage(int secondLanguage) {
        this.mSecondLang = secondLanguage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioLang audioLang = (AudioLang) o;
        return mFirstLang == audioLang.mFirstLang && mSecondLang == audioLang.mSecondLang;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFirstLang, mSecondLang);
    }

    @Override
    public String toString() {
        return "AudioLang{" +
                "firstLang=" + mFirstLang +
                ", secondLang=" + mSecondLang +
                '}';
    }
}
