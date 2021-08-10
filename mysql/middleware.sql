-- 如果没有middleware数据库，则需要先创建
-- CREATE DATABASE `middleware`
-- default character set utf8mb4
-- default collate utf8mb4_general_ci;

USE `middleware`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for middleware_info
-- ----------------------------
DROP TABLE IF EXISTS `middleware_info`;
CREATE TABLE `middleware_info` (
  `id` int(11) NOT NULL COMMENT '自增id',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '中间件名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型',
  `version` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '版本',
  `image` mediumblob NOT NULL COMMENT '图片',
  `image_path` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图片地址',
  `status` tinyint(1) DEFAULT '1' COMMENT '是否可用：0-不可用 1-可用',
  `chart_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'chart包名称',
  `chart_version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'chart版本',
  `grafana_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'grafana的id',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modifier` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='中间件表';

-- ----------------------------
-- Records of middleware_info
-- ----------------------------
BEGIN;
INSERT INTO `middleware_info` VALUES (1, 'Mysql', '关系型数据库管理系统', 'Database', '8.0.21', 0x6D7973716C2E706E67, 'mysql.png', 1, 'mysql', '0.1.0', 'uaKUl7_Mk', NULL, '2020-01-15 17:00:00', NULL, '2020-01-15 17:00:00');
INSERT INTO `middleware_info` VALUES (2, 'Redis', '开源的可基于内存亦可持久化的Key-Value数据库', 'Database', '5.0.8', 0x72656469732E706E67, 'redis.png', 1, 'redis', '5.0.8', 'z8mdPzYMz', NULL, '2020-01-15 17:00:00', NULL, '2020-01-15 17:00:00');
INSERT INTO `middleware_info` VALUES (3, 'Elasticsearch', '基于Lucene的搜索服务器', 'Database', '6.8.10', 0x656C61737469632E706E67, 'elastic.png', 1, 'elasticsearch', '6.8.10-1', 'testaer', NULL, '2020-01-15 17:00:00', NULL, '2020-01-15 17:00:00');
INSERT INTO `middleware_info` VALUES (4, 'RocketMQ', '阿里巴巴开源的消息中间件', 'MQ', '4.1.0', 0x726F636B65746D712E706E67, 'rocketmq.png', 1, 'rocketmq', '4.1.0', 'zkVx1w_izdd', NULL, '2020-01-15 17:00:00', NULL, '2020-01-15 17:00:00');
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;