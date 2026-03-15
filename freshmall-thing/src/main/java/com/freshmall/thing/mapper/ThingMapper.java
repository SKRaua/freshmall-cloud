package com.freshmall.thing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshmall.common.entity.Thing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ThingMapper extends BaseMapper<Thing> {

    @Update("UPDATE b_thing SET repertory = repertory - #{count} WHERE id = #{thingId} AND repertory >= #{count}")
    int deductStock(@Param("thingId") String thingId, @Param("count") int count);

    @Update("UPDATE b_thing SET pv = CAST(COALESCE(NULLIF(pv, ''), '0') AS UNSIGNED) + 1 WHERE id = #{thingId}")
    int increasePv(@Param("thingId") String thingId);

    @Update("UPDATE b_thing SET repertory = repertory + #{count} WHERE id = #{thingId}")
    int increaseStock(@Param("thingId") String thingId, @Param("count") int count);
}
