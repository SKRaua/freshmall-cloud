package com.freshmall.thing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
// import com.freshmall.common.entity.Ad;
import com.freshmall.common.entity.Record;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RecordMapper extends BaseMapper<Record> {

    List<String> getIpList();
}
