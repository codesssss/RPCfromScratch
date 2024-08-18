package org.tic.compress;

import org.tic.extension.SPI;

/**
 * @author codesssss
 * @date 18/8/2024 11:13 pm
 */
@SPI
public interface Compress {

    byte[] compress(byte[] bytes);


    byte[] decompress(byte[] bytes);
}
