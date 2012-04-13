package ar.com.hjg.pngj.chunks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;

import ar.com.hjg.pngj.PngHelper;
import ar.com.hjg.pngj.PngjBadCrcException;
import ar.com.hjg.pngj.PngjOutputException;

/**
 * Raw (physical) chunk
 * <p>
 * Short lived object, to be created while serialing/deserializing Do not reuse it for different chunks
 * <p>
 * see http://www.libpng.org/pub/png/spec/1.2/PNG-Structure.html
 */
public class ChunkRaw {
	/**
	 * The length counts only the data field, not itself, the chunk type code, or the CRC. Zero is a valid length.
	 * Although encoders and decoders should treat the length as unsigned, its value must not exceed 231-1 bytes.
	 */
	public final int len;

	/**
	 * A 4-byte chunk type code. uppercase and lowercase ASCII letters
	 */
	public final byte[] idbytes = new byte[4];

	/**
	 * The data bytes appropriate to the chunk type, if any. This field can be of zero length. Does not include crc
	 */
	public byte[] data = null;
	/**
	 * A 4-byte CRC (Cyclic Redundancy Check) calculated on the preceding bytes in the chunk, including the chunk type
	 * code and chunk data fields, but not including the length field.
	 */
	private int crcval = 0;

	/**
	 * @param len
	 *            : data len
	 * @param idbytes
	 *            : chunk type (deep copied)
	 * @param alloc
	 *            : it true, the data array will be allocced
	 */
	public ChunkRaw(int len, byte[] idbytes, boolean alloc) {
		this.len = len;
		System.arraycopy(idbytes, 0, this.idbytes, 0, 4);
		if (alloc)
			allocData();
	}

	private void allocData() {
		if (data == null || data.length < len)
			data = new byte[len];
	}

	/**
	 * this is called after setting data, before writing to os
	 */
	private void computeCrc() {
		CRC32 crcengine = PngHelper.getCRC();
		crcengine.reset();
		crcengine.update(idbytes, 0, 4);
		if (len > 0)
			crcengine.update(data, 0, len); //
		crcval = (int) crcengine.getValue();
	}

	/**
	 * Computes the CRC and writes to the stream. If error, a PngjOutputException is thrown
	 */
	public void writeChunk(OutputStream os) {
		if (idbytes.length != 4)
			throw new PngjOutputException("bad chunkid [" + ChunkHelper.toString(idbytes) + "]");
		computeCrc();
		PngHelper.writeInt4(os, len);
		PngHelper.writeBytes(os, idbytes);
		if (len > 0)
			PngHelper.writeBytes(os, data, 0, len);
		PngHelper.writeInt4(os, crcval);
	}

	/**
	 * position before: just after chunk id. positon after: after crc Data should be already allocated. Checks CRC
	 * Return number of byte read.
	 */
	public int readChunkData(InputStream is) {
		PngHelper.readBytes(is, data, 0, len);
		int crcori = PngHelper.readInt4(is);
		computeCrc();
		if (crcori != crcval)
			throw new PngjBadCrcException("crc invalid for chunk " + toString() + " calc=" + crcval + " read=" + crcori);
		return len + 4;
	}

	public ByteArrayInputStream getAsByteStream() { // only the data
		return new ByteArrayInputStream(data);
	}

	public String toString() {
		return "chunkid=" + ChunkHelper.toString(idbytes) + " len=" + len;
	}
}
