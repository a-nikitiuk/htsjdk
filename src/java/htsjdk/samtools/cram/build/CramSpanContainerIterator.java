package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An iterator of CRAM containers read from locations in {@link htsjdk.samtools.seekablestream.SeekableStream}. The locations are specified with
 * pairs of coordinates, they are basically file pointers as returned for example by {@link htsjdk.samtools.SamReader.Indexing#getFilePointerSpanningReads()}
 */
public class CramSpanContainerIterator implements Iterator<Container> {
    private final CramHeader cramHeader;
    private final SeekableStream ss;
    private Iterator<Boundary> containerBoundaries;
    private Boundary currentBoundary;
    private long firstContainerOffset;

    private CramSpanContainerIterator(SeekableStream ss, long[] coordinates) throws IOException {
        this.ss = ss;
        ss.seek(0);
        this.cramHeader = CramIO.readCramHeader(ss);
        firstContainerOffset = ss.position();

        List<Boundary> boundaries = new ArrayList<Boundary>();
        for (int i = 0; i < coordinates.length; i += 2) {
            boundaries.add(new Boundary(coordinates[i], coordinates[i + 1]));
        }

        containerBoundaries = boundaries.iterator();
        currentBoundary = containerBoundaries.next();
    }

    public static CramSpanContainerIterator fromFileSpan(SeekableStream ss, long[] coordinates) throws IOException {
        return new CramSpanContainerIterator(ss, coordinates);
    }

    @Override
    public boolean hasNext() {
        try {
            if (currentBoundary.hasNext()) return true;
            if (!containerBoundaries.hasNext()) return false;
            currentBoundary = containerBoundaries.next();
            return currentBoundary.hasNext();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Container next() {
        try {
            return currentBoundary.next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove() {
        throw new RuntimeException("Not allowed.");
    }

    public CramHeader getCramHeader() {
        return cramHeader;
    }

    private class Boundary {
        final long start;
        final long end;

        public Boundary(long start, long end) {
            this.start = start;
            this.end = end;
            if (start >= end) throw new RuntimeException("Boundary start is greater than end.");
        }

        boolean hasNext() throws IOException {
            return ss.position() <= (end >> 16);
        }

        Container next() throws IOException {
            if (ss.position() < (start >> 16)) ss.seek(start >> 16);
            if (ss.position() > (end >> 16)) throw new RuntimeException("No more containers in this boundary.");
            long offset = ss.position();
            Container c = ContainerIO.readContainer(cramHeader.getVersion(), ss);
            c.offset = offset;
            return c;
        }
    }

    public long getFirstContainerOffset() {
        return firstContainerOffset;
    }
}
