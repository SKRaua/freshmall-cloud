package com.freshmall.thing.service;


// import com.freshmall.common.entity.Ad;
import com.freshmall.common.entity.Record;

import java.util.List;

public interface RecordService {
    List<Record> getRecordList();
    void createRecord(Record record);
    void updateRecord(Record record);

    Record getRecord(Long thingId, String ip);

    List<String> getRecordIpList();

    List<Record> getRecordListByIp(String ip);
}
