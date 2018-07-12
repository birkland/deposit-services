/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.deposit.messaging.support.swordv2;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.http.ParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.InputStream;

import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_ARCHIVED;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_INREVIEW;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_WITHDRAWN;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.ARCHIVED_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.INPROGRESS_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.INREVIEW_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.MISSING_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.MULTIPLE_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.UNKNOWN_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.WITHDRAWN_STATUS_RESOURCE;
import static resources.SharedResourceUtil.findStreamByName;
import static resources.SharedResourceUtil.findUriByName;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AtomFeedStatusParserTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Parser abderaParser;

    private AtomFeedStatusParser underTest;

    @Before
    public void setUp() throws Exception {
        abderaParser = mock(Parser.class);
        underTest = new AtomFeedStatusParser(abderaParser);
    }

    // Test cases
    //   - Deposit with malformed status ref (can't, cause method requires URI)
    //   - Deposit with status ref that doesn't exist (test of AbderaClient)
    //   - Deposit with status ref that times out (test of AbderaClient)
    //   - Deposit status is mapped to a non-terminal status (should be left alone)
    //   - Deposit status is mapped to null
    //   - Deposit with AbderaClient that throws an exception
    //   - Parse a Document<Feed> with missing or incorrect Category
    //   - Parse a Document<Feed> with correct Category but unknown value


    /**
     * An Atom Statement containing a <sword:state> of http://dspace.org/state/archived should be parsed
     * @throws Exception
     */
    @Test
    public void mapArchived() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(ARCHIVED_STATUS_RESOURCE, AtomResources.class));
        Assert.assertEquals(SWORD_STATE_ARCHIVED, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapInProgress() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(INPROGRESS_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_INPROGRESS, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapInReview() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(INREVIEW_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_INREVIEW, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapMissing() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(MISSING_STATUS_RESOURCE, AtomResources.class));
        assertEquals(null, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapMultiple() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(MULTIPLE_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_ARCHIVED, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapUnknown() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(UNKNOWN_STATUS_RESOURCE, AtomResources.class));
        assertEquals(null, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapWithdrawn() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(WITHDRAWN_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_WITHDRAWN, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void parseWithRuntimeException() throws Exception {
        RuntimeException expected = new RuntimeException("Expected exception.");
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Expected exception.");
        expectedException.expectMessage("AtomStatusParser-archived.xml");
        when(abderaParser.parse(any(InputStream.class))).thenThrow(expected);

        underTest.parse(findUriByName(ARCHIVED_STATUS_RESOURCE, AtomResources.class));
    }

    @Test
    public void parseWithParseException() throws Exception {
        ParseException expectedCause = new ParseException("Expected cause.");
        expectedException.expect(RuntimeException.class);
        expectedException.expectCause(is(expectedCause));
        expectedException.expectMessage("Expected cause.");
        expectedException.expectMessage("AtomStatusParser-archived.xml");

        when(abderaParser.parse(any(InputStream.class))).thenThrow(expectedCause);

        underTest.parse(findUriByName(ARCHIVED_STATUS_RESOURCE, AtomResources.class));
    }
}