package ar.com.hjg.pngj.chunks;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngjOutputException;

public class ChunksListForWrite extends ChunksList {

	/**
	 * chunks not yet writen - does not include IHDR, IDAT, END, perhaps yes PLTE
	 */
	private final List<PngChunk> queuedChunks = new ArrayList<PngChunk>();

	// redundant, just for eficciency
	private HashSet<String> alreadyWrittenKeys = new HashSet<String>();

	public ChunksListForWrite(ImageInfo imfinfo) {
		super(imfinfo);
	}

	/**
	 * Remove Chunk: only from queued
	 * 
	 * WARNING: this depends on c.equals() implementation, which is straightforward for SingleChunks. For
	 * MultipleChunks, it will normally check for reference equality!
	 */
	public boolean removeChunk(PngChunk c) {
		return queuedChunks.remove(c);
	}

	/**
	 * behaviour:
	 * 
	 * 
	 * @param c
	 */
	public boolean queue(PngChunk c) {
		queuedChunks.add(c);
		return true;
	}

	/**
	 * this should be called only for ancillary chunks and PLTE (groups 1 - 3 - 5)
	 **/
	private static boolean shouldWrite(PngChunk c, int currentGroup) {
		if (currentGroup == CHUNK_GROUP_2_PLTE)
			return c.id.equals(ChunkHelper.PLTE);
		if (currentGroup % 2 == 0)
			throw new RuntimeException("?");
		int minChunkGroup, maxChunkGroup;
		if (c.getOrderingConstraint().mustGoBeforePLTE())
			minChunkGroup = maxChunkGroup = ChunksList.CHUNK_GROUP_1_AFTERIDHR;
		else if (c.getOrderingConstraint().mustGoBeforeIDAT()) {
			maxChunkGroup = ChunksList.CHUNK_GROUP_3_AFTERPLTE;
			minChunkGroup = c.getOrderingConstraint().mustGoAfterPLTE() ? ChunksList.CHUNK_GROUP_3_AFTERPLTE
					: ChunksList.CHUNK_GROUP_1_AFTERIDHR;
		} else {
			maxChunkGroup = ChunksList.CHUNK_GROUP_5_AFTERIDAT;
			minChunkGroup = ChunksList.CHUNK_GROUP_1_AFTERIDHR;
		}

		int preferred = maxChunkGroup;
		if (c.hasPriority())
			preferred = minChunkGroup;
		if (ChunkHelper.isUnknown(c) && c.getChunkGroup() > 0)
			preferred = c.getChunkGroup();
		if (currentGroup == preferred)
			return true;
		if (currentGroup > preferred && currentGroup <= maxChunkGroup)
			return true;
		return false;
	}

	public int writeChunks(OutputStream os, int currentGroup) {
		int cont = 0;
		Iterator<PngChunk> it = queuedChunks.iterator();
		while (it.hasNext()) {
			PngChunk c = it.next();
			if (!shouldWrite(c, currentGroup))
				continue;
			if (ChunkHelper.isCritical(c.id) && !c.id.equals(ChunkHelper.PLTE))
				throw new PngjOutputException("bad chunk queued: " + c);
			if (alreadyWrittenKeys.contains(c.id) && !c.allowsMultiple())
				throw new PngjOutputException("duplicated chunk does not allow multiple: " + c);
			c.write(os);
			chunks.add(c);
			alreadyWrittenKeys.add(c.id);
			c.setChunkGroup(currentGroup);
			it.remove();
			cont++;
		}
		return cont;
	}

	/**
	 * warning: this is NOT a copy, do not modify
	 */
	public List<PngChunk> getQueuedChunks() {
		return queuedChunks;
	}

	public String toString() {
		return "ChunkList: written: " + chunks.size() + " queue: " + queuedChunks.size();
	}

	/**
	 * for debugging
	 */
	public String toStringFull() {
		StringBuilder sb = new StringBuilder(toString());
		sb.append("\n Written:\n");
		for (PngChunk chunk : chunks) {
			sb.append(chunk).append(" G=" + chunk.getChunkGroup() + "\n");
		}
		if (!queuedChunks.isEmpty()) {
			sb.append(" Queued:\n");
			for (PngChunk chunk : queuedChunks) {
				sb.append(chunk).append("\n");
			}

		}
		return sb.toString();
	}
}
