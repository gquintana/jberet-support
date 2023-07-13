/*
 * Copyright (c) 2015 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.jberet.support.io;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Base class for bean classes that represent stock trade data.
 * The CSV file, IBM_unadjusted.txt, is downloaded from kibot.com free trial data:
 * http://www.kibot.com/buy.aspx
 * http://api.kibot.com/?action=history&symbol=IBM&interval=1&unadjusted=1&bp=1&user=guest
 * <p/>
 * The data is in standard CSV format with order of fields:
 * Date,Time,Open,High,Low,Close,Volume
 * <p/>
 * The data file contains no header line.  The data file is truncated to 10/13/2008,12:09 to stay within Excel row
 * number limit (1048576 rows max).
 *
 * @see StockTrade
 * @see StockTradeWithJoda
 */

public abstract class StockTradeBase implements Serializable {
    private static final long serialVersionUID = 1393270867555404840L;

    @NotNull
    @Size(min = 3, max = 5)
    @Pattern(regexp = "^([0-9]|0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")
    @JsonProperty("Time")
    String time;

    @NotNull
    @Max(1000)
    @Min(1)
    @JsonProperty("Open")
    double open;

    @Max(1000)
    @Min(1)
    @JsonProperty("High")
    double high;

    @Max(1000)
    @Min(1)
    @JsonProperty("Low")
    double low;

    @Max(1000)
    @Min(1)
    @JsonProperty("Close")
    double close;

    @NotNull
    @DecimalMin("100")
    @DecimalMax("9999999999")
    @JsonProperty("Volume")
    double volume;

    public String getTime() {
        return time;
    }

    public void setTime(final String time) {
        this.time = time;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(final double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(final double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(final double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(final double close) {
        this.close = close;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(final double volume) {
        this.volume = volume;
    }
}
