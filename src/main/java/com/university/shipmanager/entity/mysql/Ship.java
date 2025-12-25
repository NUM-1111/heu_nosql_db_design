package com.university.shipmanager.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("ship")
public class Ship {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;       // 船名 (如: 远望7号)
    private String code;       // 舷号/IMO (如: YW-7)
    private String owner;      // 船东
    private LocalDate builtAt; // 建造日期
}