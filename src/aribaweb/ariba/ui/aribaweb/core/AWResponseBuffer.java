/*
    Copyright 1996-2008 Ariba, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    $Id: //ariba/platform/ui/aribaweb/ariba/ui/aribaweb/core/AWResponseBuffer.java#17 $
*/

package ariba.ui.aribaweb.core;

import ariba.ui.aribaweb.util.AWBaseObject;
import ariba.ui.aribaweb.util.AWCharacterEncoding;
import ariba.ui.aribaweb.util.AWEncodedString;
import ariba.ui.aribaweb.util.AWGenericException;
import ariba.ui.aribaweb.util.AWPagedVector;
import ariba.ui.aribaweb.util.AWUtil;
import ariba.util.core.Assert;
import ariba.util.core.ListUtil;
import ariba.util.core.StringUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * High-level notes on the algorithm:
 *
 * A responseBuffer defines a region of the responseContents (ie the encodedStrings appended to the response during
 * renderResponse).  ResponseBuffers can be nested.  There are two types: scoped and regular.  Let's first
 * describe how a regular buffer works then talk about the scoped.
 *
 * RegularBuffers:  The entire page is in a regular buffer.  Within that page there can be regions (div/spans)
 * that can change when cycling the page.  If we make each of these into a sub-buffer, we'll have nested regular
 * buffers.  To detect the minimum amount of text that needs to be written to the response, we need to find the
 * smallest buffers that changed.  If a buffer is determined to be changed, all its content must be re-written.
 *
 * We talk in terms of the "top-level" content of a buffer.  This is all the encodedStrins in the buffer but not the
 * contents of the subbuffers, which are treated independently.  However, we do concern ourselves with the names of
 * the subbuffers because if these change, we will have had a deletion or insertion and the only way to handle that
 * in general is to rewrite the entire buffer.
 *
 * We employ checksums to substitute for actual string comparisons.  The checksum for a regularBuffer is computed
 * by including all the encodedStrings at the top-level of a given regular buffer, plus the names of the subbuffers.
 * If any top-level string of subbuffer name changed, we detect a change and must write the entire buffer.
 *
 * If we do not detect a change in the top-level content of the buffer (ie the checksums are the same), we still need
 * to recurse down to the next level to see if any of the contents of the subbuffers changed and needs to be written
 * out.  We simply iterate through the children of the current buffer and repeat the aforementioned process.
 *
 * However, there is a second type of buffer: scoped buffers.  An example of a scoped buffer is a <table></table>.
 * In a table, we can detect and react to insertions, deletions, and modifications of rows, but this requires a
 * different algorithm than regular buffers.
 *
 * First, to know if we need to write the entire scoped buffer, we compare its top-level content to its predecessor.
 * In this case, we only care about the encodedStrings at the top level and not the names of the children buffers.
 * Changes in child buffers names are an indication of insertions or deletions and we have a way to deal with that.
 * In any case, if we detect a change in the top level content of a scoped buffer, we simply write out the entire
 * buffer.
 *
 * However, if we detect a change in a child buffer (either insertion, deletion, or modification), we must still
 * write out the top-level contents of the scoped buffer as this is generally required to be legitimate html (that is,
 * a <tr/> cannot exist in the absence of a <table/>).  So to write the children of a scoped buffer, we iterate through
 * the encodedStrings at the top level which are intermingled with the subbuffers.  We write all encodedStrings,
 * but must determine what to do with each subbuffer.  If the subbuffer is a simple modification at the top level, its
 * checksum will differ from its predecessor and we simply write the entire child buffer.
 *
 * If the child buffer doesn't exist in the previous response, then we have an insertion and this means we must
 * write out the entire buffer and generate some javascript to tell the client side code that this entry is an
 * insertion.  For insertions, we must indicate which row it comes after so the client side code can insert the
 * row in the proper place.
 *
 * Once we've iterated through all children in the scoped buffer and written all the top-level content of the
 * scoped buffer, we must iterate through its predecessor to determine if any rows were present in the predecessor
 * but not in the current scoped buffer -- this would be a deletion.  All deletions can be written after the scoped
 * buffer has been written.
 *
 * Finally, we must write out any sub-subbuffers nested within a child of a scoped buffer which wasn't written in
 * the previous passes.  That is, if a given child had no changes and didn't require writing within the scoped buffer,
 * we need to propagate the writeTo function to each of the nested buffers within those children.
 *
 * Notes on the implementation of AWResponseBuffer:
 *
 * To minimize garbage generation, we chose to use a single "contents" buffer and have the AWResponseBuffers point
 * into this PagedVector.  So the BaseResponse allocates a PagedVector which is shared by all responseBuffers and
 * the responseBuffers simply maintin indexes to their start and ending positions.  Each time a new subbuffer
 * is required by the AWRefreshRegion component, a new one is created and initialized with the index of the globalContents
 * buffer.  The baseResponse makes this buffer the current target buffer and all "append" is done to this buffer.
 * Of course, the target buffer puts the appended content in the shared globalCotents buffer, but it keep track
 * of what's going on by updating its checksum as content is appended.
 *
 * When a buffer is appended it updates its _children list by adding the buffer to the end of the list.  Each
 * responseBuffer has a _children pointer which points to the head of the children, and a _next pointer which
 * points to the next child in the current list.  We also maintain a _tail which allows for rapid append to the
 * end of the list without requiring iteration to the end to simply append a new child.
 *
 * If a buffer is a scoped buffer, appending a child does something a bit different than a regular buffer.
 * In a regular buffer, we don't really care to know where the begin/end of a given child buffer is because we
 * are either going to write the entire buffer or merely its children.  With the proper use of checksums,
 * we can determine which to do quite easily and quickly (se discussion above).
 *
 * However, with a scoped buffer, we are required to write the top-level content of the scoped buffer and optionally
 * write some of the children.  This means we must iterate through the top-level encodedStrings writing them out
 * but, at the appropriate point, conditionally write out a child buffer.  Hence, we must put the buffers themselves
 * in the globalContents to keep track of when to compare/write them.  Once we've determined that some child
 * requires writing, we must render the wrapper of the scoped buffer (eg the <table>...</table> tags) and all
 * the interstitial content between the rows of the table (ie the children).  As we go, we encounter child buffers
 * and optionally write them out (by comparing their checksums to their predecesor).
 *
 * So, when we append a child buffer to a scoped buffer, we add this buffer to the content so it can be invovled
 * in the rendering of the wrapper.  However, we do not include its name in the scoped buffer's checksum as with
 * regular buffers because we will deal with each child buffer in situ as either a modification or insertion.
 *
 * To make the determination of whether or not a child buffer was an insertion or deletion, we keep a HashMap
 * of all scoped child buffers in both the current and previous responseBuffers.  Thus, we can easily compare
 * the current children with the previous and determine if it existed before or not.  Of course, we can do the same
 * from the other direction to determine if there were deletions, and this is done after the scoped buffer is written
 * in its entirety.
 *
 * Finally, we must make a pass throught he children of the scoped buffer and, for those children which were
 * not written out in the previous pass, give them an opportunity to write out any of their subbuffers which may have
 * changed.  Again, this is done outside the rendering of the scoped buffer to result in legitimate html.
 *
 * Other notes:  This whole operation is an interactive dance with the BaseResponse.  BaseResponse maintains a stack
 * of buffers and the current buffer so it knows where to direct the next append operation.  As it pops buffers
 * off its stack, it sends a close() message to that buffer so that buffer can cleanup (ie release its CRC32) and,
 * most importantly, take note of the size of the globalContents buffer so it knows where its end is.
 *
 * Pooling: To avoid too much garbage generation, we use a recycle pool for the CRC32 objects and for
 * the AWPagedVectorIterator.  In both cases, the number of objects required at any one time is a function of the
 * depth of the stack of responseBuffers, so the pool neededn't be too large.  However, they come and go quite
 * frequently, so its make sense to pool these.  Also, they clean up nicely, so its easy to pool them.
 *
 * I do not pool AWResponseBuffers since they are quite numerous and have a fairly long life.
 *
 * Also, once a BaseResponse has been written to the client, we jettison the globalContent to free up that memory.
 */
public final class AWResponseBuffer extends AWBaseObject
{
    private final AWEncodedString _name;
    private final boolean _alwaysRender;
    private final boolean _isScope;
    private final AWBaseResponse _baseResponse;
    private final HashMap _globalScopeChildren;
    private int _contentsStartIndex;
    private int _contentsEndIndex;
    private boolean _ignoreWhitespaceDiffs;

    private AWPagedVector _globalContents;

    // AWChecksum is a java implementation of the CRC32 checksum, which does not use
    // the java.util.zip.Checksum interface and thus does not require creating instances
    // of the Checksum object (and thus additional overhead of GC / pooling / etc.).
    private long _awchecksumValue;
    private int _byteCount = 0;

    // The children are managed as a linked list.
    private AWResponseBuffer _children;
    private AWResponseBuffer _next;
    private AWResponseBuffer _tail;

    protected AWResponseBuffer (AWEncodedString name, boolean isScope, boolean alwaysRender, AWBaseResponse baseResponse)
    {
        _baseResponse = baseResponse;
        _globalContents = baseResponse.globalContents();
        _name = name;
        Assert.that(_name != null, "name may not be null.");
        _isScope = isScope;
        _ignoreWhitespaceDiffs = isScope;
        _globalScopeChildren = _isScope ? _baseResponse.scopeChildren() : null;
        _alwaysRender = alwaysRender;
        _contentsStartIndex = _globalContents.size();
        _contentsEndIndex = _contentsStartIndex;
    }

    private AWResponseBuffer (AWEncodedString name)
    {
        _name = name;
        _alwaysRender = _isScope = false;
        _baseResponse = null;
        _globalScopeChildren = null;
    }

    protected void close ()
    {
        _contentsEndIndex = _globalContents.size();
    }

    public void updateParentChecksum (AWResponseBuffer parent)
    {
        // used to attribute our checksum to the rootBuffer to force FPR for globalScope
        // noRefresh buffers.
        // We encode both our data (checksum, length) but not our name so that a change
        // in our contents will trigger an FPR, but not a change in our position (elementId)
        parent._byteCount += _byteCount;
        parent._awchecksumValue = AWChecksum.crc32(parent._awchecksumValue, _awchecksumValue);
    }

    protected boolean isEqual (AWResponseBuffer otherBuffer)
    {
        return (_awchecksumValue == otherBuffer._awchecksumValue) &&
                (_byteCount == otherBuffer._byteCount);
    }

    private void updateChecksum (AWEncodedString encodedString)
    {
        byte[] bytes = encodedString.bytes(AWCharacterEncoding.UTF8);
        int bytesLength = bytes.length;
        _awchecksumValue = AWChecksum.crc32(_awchecksumValue, bytes, bytesLength);
        _byteCount += bytesLength;
    }

    public void setIgnoreWhitespaceDiffs (boolean yn)
    {
        _ignoreWhitespaceDiffs = yn;
    }

    //////////////////
    // Child Handling
    //////////////////
    private AWResponseBuffer addNext (AWResponseBuffer responseBuffer)
    {
        if (_next == null) {
            _next = _tail = responseBuffer;
        }
        else {
            _tail = _tail.addNext(responseBuffer);
        }
        return _tail;
    }

    private void addChild (AWResponseBuffer responseBuffer)
    {
        if (_children == null) {
            _children = responseBuffer;
        }
        else {
            _children.addNext(responseBuffer);
        }
    }

    ////////////////////
    // External methods
    ////////////////////
    protected void append (AWEncodedString encodedString)
    {
        if (encodedString != null) {
            _globalContents.add(encodedString);
            if (!_ignoreWhitespaceDiffs || !StringUtil.nullOrEmptyOrBlankString(encodedString.string())) {
                // For scoped buffers, we do not include pure whitespace in its checksum
                updateChecksum(encodedString);

                // remember the size for perf measurement
                _baseResponse._fullSize += encodedString.bytes(AWCharacterEncoding.UTF8).length;
            }
        }
    }

    protected void append (AWResponseBuffer responseBuffer)
    {
        if (responseBuffer != null) {
            Assert.that(!responseBuffer._isScope || !_isScope, "Attempt to nest scoped RefreshRegion directly inside another");
            addChild(responseBuffer);
            // add the response buffer to the global contents and increment the
            // response buffer's _contentsStartIndex so it doesn't point to itself
            _globalContents.add(responseBuffer);
            responseBuffer._contentsStartIndex++;
            if (_isScope) {
                // For scoped buffers, we do not include the children buffers in its checksum
                _globalScopeChildren.put(responseBuffer._name, responseBuffer);
            }
            else {
                updateChecksum(responseBuffer._name);
            }
        }
    }

    static class ScopeChanges {
        List inserts, updates, deletes;
        int total;
    }


    // Number of modifications that should trigger a full table replace instead of an piece-wise update
    protected static int ScopeChildCountUpdateAllThreshhold = 50;
    
    /**
     * This is the eternal entry point called by the AWBaseResponse instance.
     * @param outputStream
     * @param characterEncoding
     * @param otherBuffer
     */
    protected void writeTo (OutputStream outputStream, AWCharacterEncoding characterEncoding, AWResponseBuffer otherBuffer)
    {
        if (_alwaysRender || otherBuffer == null || !isEqual(otherBuffer)) {
            renderAll(outputStream, characterEncoding);
            if (_isScope) {
                writeScopeUpdate(outputStream, characterEncoding);
            }
        }
        else if (_isScope) {
            // We either need the wrapper or we do not.  We do not need the wrapper if all children checksums match,
            // which means there were no insertions, deletions, or modifications to the top-level content of any row.
            // Note: it may be more efficient to simply extract the inserted/deleted/modified/unmodified lists rather
            // than doing this boolean check -- at least we'd get some work done during the iteration.
            ScopeChanges changes = checkScopeChanges(otherBuffer);
            if (changes != null) {
                if (changes.total > ScopeChildCountUpdateAllThreshhold) {
                    // write out the whole table
                    renderAll(outputStream, characterEncoding);
                    writeScopeUpdate(outputStream, characterEncoding);
                } else {
                    if (changes.inserts != null || changes.updates != null) {
                        // Now write out the table and its modified children, including insertions.  At the end, write out
                        // the deltedChildren as javascript calls.
                        writeScopedBuffer(outputStream, characterEncoding, otherBuffer);

                        // Now, outside the table, write any buffers which changed within rows that didn't change.
                        writeUnmodifiedChildren(outputStream, characterEncoding, otherBuffer);
                    } else {
                        // write changes within rows (if any)
                        writeNextSublevel(outputStream, characterEncoding, otherBuffer);
                    }
                    // JS to execute inserts and deletes
                    if (changes.inserts != null || changes.deletes != null) {
                        writeScopeChangeScript(outputStream, characterEncoding, changes.inserts, changes.deletes);
                    }
                }
            }
            else {
                writeNextSublevel(outputStream, characterEncoding, otherBuffer);
            }
        }
        else {
            writeNextLevel(outputStream, characterEncoding, otherBuffer);
        }
    }

    /**
     * When its determined that a given buffer should be rendered in its entirety, call this.
     * @param outputStream
     * @param characterEncoding
     */
    private void renderAll (OutputStream outputStream, AWCharacterEncoding characterEncoding)
    {
        try {
            AWPagedVector.AWPagedVectorIterator elements = _globalContents.elements(_contentsStartIndex, _contentsEndIndex);
            while (elements.hasNext()) {
                Object element = elements.next();
                if (element instanceof AWEncodedString) {
                    byte[] bytes = ((AWEncodedString)element).bytes(characterEncoding);
                    write(outputStream, bytes);
                }
                else {
                    AWResponseBuffer childBuffer = (AWResponseBuffer)element;
                    childBuffer.renderAll(outputStream, characterEncoding);
                    elements.skipTo(childBuffer._contentsEndIndex);
                }
            }
            elements.release();
        }
        catch (IOException ioexception) {
            throw new AWGenericException(ioexception);
        }
    }


    /**
     * This should ONLY be used by regular buffers.  It skips all the string content at the top level and
     * asks each child to determine if it requires writing.
     * @param outputStream
     * @param characterEncoding
     * @param otherBuffer
     */
    private void writeNextLevel (OutputStream outputStream, AWCharacterEncoding characterEncoding, AWResponseBuffer otherBuffer)
    {
        Assert.that(_isScope == false, "writeNextLevel(...) cannot be used by scoped buffers");
        AWResponseBuffer childBuffer = _children;
        AWResponseBuffer otherChildBuffer = otherBuffer._children;
        while (childBuffer != null) {
            childBuffer.writeTo(outputStream, characterEncoding, otherChildBuffer);
            childBuffer = childBuffer._next;
            otherChildBuffer = otherChildBuffer._next;
        }
    }

    /**
     * This is used to write the buffers contained within the children of a scoped buffer (ie a div within a tablecell)
     * This should only be called on scoped children when its determined that the wrapper scope doesn't require
     * writing (ie all children are same in terms of their checksums)
     * @param outputStream
     * @param characterEncoding
     * @param otherBuffer
     */
    private void writeNextSublevel (OutputStream outputStream, AWCharacterEncoding characterEncoding, AWResponseBuffer otherBuffer)
    {
        Assert.that(_isScope, "writeNextSublevel(...) can only be used by scoped buffers");
        AWResponseBuffer childBuffer = _children;
        HashMap otherScopeChildren = otherBuffer._globalScopeChildren;
        while (childBuffer != null) {
            AWEncodedString childBufferName = childBuffer._name;
            AWResponseBuffer otherChildBuffer = (AWResponseBuffer)otherScopeChildren.get(childBufferName);
            childBuffer.writeNextLevel(outputStream, characterEncoding, otherChildBuffer);
            childBuffer = childBuffer._next;
        }
    }

    private static AWResponseBuffer _NullResponseRef = new AWResponseBuffer(AWConstants.Null);

    ScopeChanges checkScopeChanges (AWResponseBuffer otherBuffer)
    {
        ScopeChanges changes = null;
        int total = 0;
        List inserts = null, updates = null, deletes = null;

        // insertions and updates
        AWPagedVector.AWPagedVectorIterator elements =
                _globalContents.elements(_contentsStartIndex, _contentsEndIndex);
        HashMap otherScopeChildren = otherBuffer._globalScopeChildren;
        AWResponseBuffer previousChild = _NullResponseRef;
        while (elements.hasNext()) {
            Object element = elements.next();
            if (!(element instanceof AWEncodedString)) {
                AWResponseBuffer childBuffer = (AWResponseBuffer)element;
                AWEncodedString childBufferName = childBuffer._name;
                AWResponseBuffer otherChildBuffer = (AWResponseBuffer)otherScopeChildren.get(childBufferName);
                if (otherChildBuffer == null) {
                    if (inserts == null) inserts = ListUtil.list();
                    inserts.add(previousChild);
                    inserts.add(childBuffer);
                    total++;
                } else if (!childBuffer.isEqual(otherChildBuffer) || childBuffer._alwaysRender) {
                    if (updates == null) updates = ListUtil.list();
                    updates.add(childBuffer);
                    total++;
                }
                elements.skipTo(childBuffer._contentsEndIndex);
                previousChild = childBuffer;                
            }
        }
        elements.release();

        // deletions
        AWResponseBuffer otherChildBuffer = otherBuffer._children;
        while (otherChildBuffer != null) {
            AWEncodedString otherChildBufferName = otherChildBuffer._name;
            if (_globalScopeChildren.get(otherChildBufferName) == null) {
                if (deletes == null) deletes = ListUtil.list();
                deletes.add(otherChildBuffer);
                total++;
            }
            otherChildBuffer = otherChildBuffer._next;
        }

        if (total > 0) {
            changes = new ScopeChanges();
            changes.total = total;
            changes.inserts = inserts;
            changes.updates = updates;
            changes.deletes = deletes;
        }

        return changes;
    }

    /**
     * This is called when it has been determined that the scope wrapper is required due to at least on of the scoped
     * children being different at the top level.  Thus we write the wrapper itself and all changed children, including
     * insertions.
     * @param outputStream
     * @param characterEncoding
     * @param otherBuffer
     */
    private void writeScopedBuffer (OutputStream outputStream, AWCharacterEncoding characterEncoding, AWResponseBuffer otherBuffer)
    {
        try {
            AWResponseBuffer previousChild = _NullResponseRef;
            AWPagedVector.AWPagedVectorIterator elements =
                    _globalContents.elements(_contentsStartIndex, _contentsEndIndex);
            HashMap otherScopeChildren = otherBuffer._globalScopeChildren;
            while (elements.hasNext()) {
                Object element = elements.next();
                if (element instanceof AWEncodedString) {
                    byte[] bytes = ((AWEncodedString)element).bytes(characterEncoding);
                    write(outputStream, bytes);
                }
                else {
                    AWResponseBuffer childBuffer = (AWResponseBuffer)element;
                    AWEncodedString childBufferName = childBuffer._name;
                    AWResponseBuffer otherChildBuffer = (AWResponseBuffer)otherScopeChildren.get(childBufferName);
                    if (otherChildBuffer == null || !childBuffer.isEqual(otherChildBuffer) || childBuffer._alwaysRender) {
                        childBuffer.renderAll(outputStream, characterEncoding);
                        if (otherChildBuffer == null) {
                            // This was an insertion -- output some javascript to denote that fact.
                            // AWEncodedString previousChildName = previousChild._name;
                            // writeInsertion(outputStream, characterEncoding, previousChildName, childBufferName);
                        }
                    }
                    elements.skipTo(childBuffer._contentsEndIndex);
                    previousChild = childBuffer;
                }
            }
            elements.release();
        }
        catch (IOException ioexception) {
            throw new AWGenericException(ioexception);
        }
    }

    private static final AWEncodedString WriteScopeUpdate1 = new AWEncodedString("<script>parent.ariba.Refresh.registerScopeUpdate('");
    private static final AWEncodedString Separator = new AWEncodedString("','");
    private static final AWEncodedString EndScript = new AWEncodedString("');</script>");
    private static final AWEncodedString WriteChanges = new AWEncodedString("<script>parent.ariba.Refresh.registerScopeChanges('");
    private static final AWEncodedString ChangeNoInsSeparator = new AWEncodedString("',null,");
    private static final AWEncodedString ChangeInsStartSeparator = new AWEncodedString("',['");
    private static final AWEncodedString ChangeInsEndSeparator = new AWEncodedString("'],");
    private static final AWEncodedString ChangeDelStartSeparator = new AWEncodedString("['");
    private static final AWEncodedString ChangeDelEndSeparator = new AWEncodedString("']);</script>");
    private static final AWEncodedString ChangeNoDelSeparator = new AWEncodedString("null);</script>");

    private void writeScopeUpdate (OutputStream outputStream, AWCharacterEncoding characterEncoding)
    {
        try {
            write(outputStream, WriteScopeUpdate1.bytes(characterEncoding));
            write(outputStream, _name.bytes(characterEncoding));
            write(outputStream, EndScript.bytes(characterEncoding));
        }
        catch (IOException ioexception) {
            throw new AWGenericException(ioexception);
        }
    }

    private void writeScopeChangeScript (OutputStream outputStream, AWCharacterEncoding characterEncoding,
            List <AWResponseBuffer>insertions, List <AWResponseBuffer>deletions)
    {
        // registerScopeChanges('tableId', [ insertions ], [ deletions ]);
        try {
            write(outputStream, WriteChanges.bytes(characterEncoding));
            write(outputStream, _name.bytes(characterEncoding));

            // insertions
            if (insertions != null) {
                write(outputStream, ChangeInsStartSeparator.bytes(characterEncoding));
                for (int i=0, c=insertions.size(); i < c; i+=2) {
                    write(outputStream, insertions.get(i)._name.bytes(characterEncoding));
                    write(outputStream, Separator.bytes(characterEncoding));
                    write(outputStream, insertions.get(i+1)._name.bytes(characterEncoding));
                    if (i + 2 < c) write(outputStream, Separator.bytes(characterEncoding));
                }
                write(outputStream, ChangeInsEndSeparator.bytes(characterEncoding));
            } else {
                write(outputStream, ChangeNoInsSeparator.bytes(characterEncoding));
            }
            // deletions
            if (deletions != null) {
                write(outputStream, ChangeDelStartSeparator.bytes(characterEncoding));
                for (int i=0, c=deletions.size(); i < c; i++) {
                    write(outputStream, deletions.get(i)._name.bytes(characterEncoding));
                    if (i + 1 < c) write(outputStream, Separator.bytes(characterEncoding));
                }
                write(outputStream, ChangeDelEndSeparator.bytes(characterEncoding));
            } else {
                write(outputStream, ChangeNoDelSeparator.bytes(characterEncoding));
            }

        }
        catch (IOException ioexception) {
            throw new AWGenericException(ioexception);
        }
    }

    /**
     * This is used to write the changed contents of unmodified rows in a table.  Many of these will not end up writing
     * anything, but if there's a domsync block within a row that is changed, this will write that out.  This is called
     * after the table is finished rendering so that these changes do not end up within the <table></table> tags.
     * @param outputStream
     * @param characterEncoding
     * @param otherBuffer
     */
    private void  writeUnmodifiedChildren (OutputStream outputStream, AWCharacterEncoding characterEncoding, AWResponseBuffer otherBuffer)
    {
        HashMap otherScopeChildren = otherBuffer._globalScopeChildren;
        AWResponseBuffer childBuffer = _children;
        while (childBuffer != null) {
            AWResponseBuffer otherChildBuffer = (AWResponseBuffer)otherScopeChildren.get(childBuffer._name);
            if (otherChildBuffer != null && childBuffer.isEqual(otherChildBuffer)) {
                childBuffer.writeNextLevel(outputStream, characterEncoding, otherChildBuffer);
            }
            childBuffer = childBuffer._next;
        }
    }

    // Only to be used by _isScoped
    // Checks if any of the children buffers are different, ignoring deleted buffers.
    private boolean needsWrapper (AWResponseBuffer otherBuffer)
    {
        Assert.that(_isScope == true, "needsWrapper(...) can only be used by scoped buffers");
        HashMap otherScopeChildren = otherBuffer._globalScopeChildren;
        AWResponseBuffer childBuffer = _children;
        while (childBuffer != null) {
            AWEncodedString childName = childBuffer._name;
            AWResponseBuffer otherChildBuffer = (AWResponseBuffer)otherScopeChildren.get(childName);
            if (otherChildBuffer == null || !childBuffer.isEqual(otherChildBuffer) || childBuffer._alwaysRender) {
                return true;
            }
            childBuffer = childBuffer._next;
        }
        return false;
    }

    private void write (OutputStream os, byte bytes[]) throws IOException
    {
        int len = bytes.length;
        os.write(bytes, 0, len);
        _baseResponse._bytesWritten += len;
    }

    protected void debug_writeTopLevelOnly (OutputStream outputStream, AWCharacterEncoding characterEncoding)
    {
        int totalLength = _globalContents.size();
        int index = 0;
        AWResponseBuffer child = _children;
        while (index < totalLength) {
            int endIndex = child == null ? totalLength : child._contentsStartIndex - 1;
            Iterator iterator = _globalContents.elements(index, endIndex);
            while (iterator.hasNext()) {
                AWEncodedString string = (AWEncodedString)iterator.next();
                AWUtil.write(outputStream, string, characterEncoding);
            }
            if (child != null) {
                index = child._contentsEndIndex;
                child = child._next;
            }
            else {
                index = totalLength;
            }
            index += 1;
        }
    }
}