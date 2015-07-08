/*
 * (c) the authors
 * Licensed under the Apache License, Version 2.0.
 */

package org.roaringbitmap.buffer;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;


/**
 * This is the underlying data structure for an ImmutableRoaringBitmap. This
 * class is not meant for end-users.
 * 
 */
public final class ImmutableRoaringArray implements PointableRoaringArray {

    protected static final short SERIAL_COOKIE = MutableRoaringArray.SERIAL_COOKIE;
    protected static final short SERIAL_COOKIE_NO_RUNCONTAINER = MutableRoaringArray.SERIAL_COOKIE_NO_RUNCONTAINER;
    private /* final static*/ int startofkeyscardinalities = 8;  // RunContainers will increase this :(
    private final static int startofrunbitmap = 8; // if there is a runcontainer bitmap

    ByteBuffer buffer;
    int size;
    boolean hasRunContainers;

    protected int unsignedBinarySearch(short k) {
        int low = 0;
        int high = this.size - 1;
        final int ikey = BufferUtil.toIntUnsigned(k);
        while (low <= high) {
            final int middleIndex = (low + high) >>> 1;
            final int middleValue = getKey(middleIndex);
            if (middleValue < ikey)
                low = middleIndex + 1;
            else if (middleValue > ikey)
                high = middleIndex - 1;
            else
                return middleIndex;
        }
        return -(low + 1);
    }
    

    /**
     * Create an array based on a previously serialized ByteBuffer.
     * 
     * @param bbf The source ByteBuffer
     */

    protected ImmutableRoaringArray(ByteBuffer bbf) {
        buffer = bbf.slice();
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        final int cookie = buffer.getInt();
        if (cookie != SERIAL_COOKIE && cookie != SERIAL_COOKIE_NO_RUNCONTAINER)
            throw new RuntimeException("I failed to find one of the right cookies. "+ cookie);
        hasRunContainers = (cookie == SERIAL_COOKIE); 

        //        System.out.println("cookie is "+cookie);
        this.size = buffer.getInt();
        //  System.out.println("IRA: size is "+size);
        if (hasRunContainers) {
            //  System.out.println("hasRunContainers is true");
            startofkeyscardinalities += 4* (( this.size+31)/32);  // account for Runcontainers bitmap
        }
        int theLimit = computeSerializedSizeInBytes();
        //        System.out.println("setting limit to "+theLimit+"whereas capacity is "+buffer.capacity());
        buffer.limit(theLimit);
    }

   
        private boolean isRunContainer( int i) {
        if (hasRunContainers) { // info is in the buffer
            int j = buffer.getInt(startofrunbitmap+4*(i/32));
            int mask = 1<<(i&31);
            return (j & mask) != 0;
        }
        else
            return false;
    }

    

    
    private int computeSerializedSizeInBytes() {

        //        System.out.println("computeSerializeSizeInBytes() with size = "+size);

        int CardinalityOfLastContainer = getCardinality(this.size - 1);
        //         System.out.println("and lastcard = "+CardinalityOfLastContainer);   
        int PositionOfLastContainer = getOffsetContainer(this.size - 1);
        int SizeOfLastContainer;
        if (isRunContainer(this.size - 1)) {

            //            System.out.println("last container is a runcontainer");

            MappeableRunContainer finalContainer = (MappeableRunContainer) getContainerAtIndex(this.size-1);
            SizeOfLastContainer = BufferUtil.getSizeInBytesFromCardinalityEtc(0,finalContainer.nbrruns, true);
            //            System.out.println("sizeoflast = "+SizeOfLastContainer+" position is "+ PositionOfLastContainer);


        }
        else {
            //            System.out.println("last container is NOT a runcontainer");
            SizeOfLastContainer = BufferUtil.getSizeInBytesFromCardinalityEtc(CardinalityOfLastContainer,0,false);

        }
        
        return SizeOfLastContainer + PositionOfLastContainer;
    }

    @Override
    public ImmutableRoaringArray clone() {
        ImmutableRoaringArray sa;
        try {
            sa = (ImmutableRoaringArray) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;// should never happen
        }
        return sa;
    }

    @Override
    public int getCardinality(int k) {
        return BufferUtil.toIntUnsigned(buffer.getShort(startofkeyscardinalities + 4 * k + 2)) + 1;
    }

    // involves a binary search
    public MappeableContainer getContainer(short x) {
        final int i = unsignedBinarySearch(x);
        if (i < 0)
            return null;
        return getContainerAtIndex(i);
    }

    public MappeableContainer getContainerAtIndex(int i) {
    	int cardinality = getCardinality(i);
        boolean isBitmap = cardinality > MappeableArrayContainer.DEFAULT_MAX_SIZE; // if not a runcontainer
        buffer.position(getOffsetContainer(i));
        if (isRunContainer(i))  {
            // first, we have a short giving the number of runs
            int nbrruns = BufferUtil.toIntUnsigned(buffer.getShort());
            //            System.out.println("IMA: getContainerAtIndex sees a runcontainer index "+i+"  with nbrruns="+nbrruns + "from "+getOffsetContainer(i));

            //            System.out.println("limit and capacity of buffer are "+ buffer.limit() + " and " + buffer.capacity());
            
            final ShortBuffer shortArray = buffer.asShortBuffer().slice();
            //            System.out.println("try to set limit to "+2*nbrruns+" from " + shortArray.limit()+" and capacity is "+shortArray.capacity());
            shortArray.limit(2*nbrruns);
            return new MappeableRunContainer(shortArray,nbrruns);
        }


        if (isBitmap) {
            final LongBuffer bitmapArray = buffer.asLongBuffer().slice();
            bitmapArray.limit(MappeableBitmapContainer.MAX_CAPACITY / 64);            
            return new MappeableBitmapContainer(bitmapArray, cardinality);
        } else {
            final ShortBuffer shortArray = buffer.asShortBuffer().slice();
            shortArray.limit(cardinality);
            return new MappeableArrayContainer(shortArray, cardinality);
        }
    }
    
    private int getOffsetContainer(int k){
        if (hasRunContainers) { // account for size of runcontainer bitmap
            //System.out.println("getOffsetContainer("+k+") for RunContainer will fetch at "+ (4 + 4 + 4 * ((this.size+31)/32) + 4 *this.size + 4*k));
            int offsetContainer = buffer.getInt(4 + 4 + 4 * ((this.size+31)/32) + 4 *this.size + 4*k);
            //System.out.println("fetched "+offsetContainer);
            return offsetContainer;
        }
        else {
            return buffer.getInt(4 + 4 + 4*this.size + 4*k);
        }
    }

    public MappeableContainerPointer getContainerPointer() {
        return getContainerPointer(0);
    }

    public MappeableContainerPointer getContainerPointer(final int startIndex) {
        return new MappeableContainerPointer() {
            int k = startIndex;

            @Override
            public void advance() {
                ++k;
            }

            @Override
            public void previous() {
                --k;
            }

			@Override
			public int compareTo(MappeableContainerPointer o) {
				if (key() != o.key())
					return BufferUtil.toIntUnsigned(key())
							- BufferUtil.toIntUnsigned(o.key());
				return o.getCardinality() - getCardinality();
			}

            @Override
            public int getCardinality() {
                return ImmutableRoaringArray.this.getCardinality(k);
            }

            @Override
            public MappeableContainer getContainer() {
                if (k >= ImmutableRoaringArray.this.size)
                    return null;
                return ImmutableRoaringArray.this.getContainerAtIndex(k);
            }

            @Override
            public boolean hasContainer() {
                return 0 <= k & k < ImmutableRoaringArray.this.size;
            }

            @Override
            public short key() {
                return ImmutableRoaringArray.this.getKeyAtIndex(k);

            }
            

            @Override
            public MappeableContainerPointer clone() {
                try {
                    return (MappeableContainerPointer) super.clone();
                } catch (CloneNotSupportedException e) {
                    return null;// will not happen
                }
            }
        };
    }

    private int getKey(int k) {
    	return BufferUtil.toIntUnsigned(buffer.getShort(startofkeyscardinalities + 4 * k));
    }

    // involves a binary search
    public int getIndex(short x) {
        return unsignedBinarySearch(x);
    }

    public short getKeyAtIndex(int i) {
        return buffer.getShort(4 * i + startofkeyscardinalities);
    }

    public int advanceUntil(short x, int pos) {
        int lower = pos + 1;

        // special handling for a possibly common sequential case
        if (lower >= size || getKey(lower) >= BufferUtil.toIntUnsigned(x)) {
            return lower;
        }

        int spansize = 1; // could set larger
        // bootstrap an upper limit

        while (lower + spansize < size && getKey(lower + spansize) < BufferUtil.toIntUnsigned(x))
            spansize *= 2; // hoping for compiler will reduce to shift
        int upper = (lower + spansize < size) ? lower + spansize : size - 1;

        // maybe we are lucky (could be common case when the seek ahead
        // expected to be small and sequential will otherwise make us look bad)
        if (getKey(upper) == BufferUtil.toIntUnsigned(x)) {
            return upper;
        }

        if (getKey(upper) < BufferUtil.toIntUnsigned(x)) {// means array has no item key >= x
            return size;
        }

        // we know that the next-smallest span was too small
        lower += (spansize / 2);

        // else begin binary search
        // invariant: array[lower]<x && array[upper]>x
        while (lower + 1 != upper) {
            int mid = (lower + upper) / 2;
            if (getKey(mid) == BufferUtil.toIntUnsigned(x))
                return mid;
            else if (getKey(mid) < BufferUtil.toIntUnsigned(x))
                lower = mid;
            else
                upper = mid;
        }
        return upper;
    }

    @Override
    public int hashCode() {
        MappeableContainerPointer cp = this.getContainerPointer();
        int hashvalue = 0;
        while (cp.hasContainer()) {
            int th = cp.key() * 0xF0F0F0 + cp.getContainer().hashCode();
            hashvalue = 31 * hashvalue + th;
        }
        return hashvalue;
    }
    /**
     * Serialize.
     * 
     * The current bitmap is not modified.
     * 
     * @param out
     *            the DataOutput stream
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public void serialize(DataOutput out) throws IOException {
        if(buffer.hasArray()) {
            out.write(buffer.array(), buffer.arrayOffset(), buffer.limit());
        } else {
            ByteBuffer tmp = buffer.duplicate();
            tmp.position(0);
            WritableByteChannel channel = Channels.newChannel((OutputStream ) out);
            channel.write(tmp);
        }
    }
    /**
     * @return the size that the data structure occupies on disk
     */
    public int serializedSizeInBytes() {
        return buffer.limit();
    }

    public int size() {
        return this.size;
    }
}
