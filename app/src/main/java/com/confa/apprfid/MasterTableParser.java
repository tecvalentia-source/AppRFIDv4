package com.confa.apprfid;
import java.util.List;
import java.util.Map;

public class MasterTableParser {
    public static class ParseResult {
        public List<MasterRecord> records;
        public Map<String, MasterRecord> byNormalizedRfid;
        public int duplicateCount;
    }
}