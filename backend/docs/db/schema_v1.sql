CREATE DATABASE  IF NOT EXISTS `ai_emotion` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `ai_emotion`;
-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: localhost    Database: ai_emotion
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `audio_analysis`
--

DROP TABLE IF EXISTS `audio_analysis`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audio_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `audio_id` bigint NOT NULL,
  `model_name` varchar(100) NOT NULL DEFAULT 'default',
  `model_version` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `summary_json` json DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_analysis_audio_time` (`audio_id`,`created_at`),
  KEY `idx_analysis_status` (`status`,`created_at`),
  CONSTRAINT `fk_analysis_audio` FOREIGN KEY (`audio_id`) REFERENCES `audio_file` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audio_analysis`
--

LOCK TABLES `audio_analysis` WRITE;
/*!40000 ALTER TABLE `audio_analysis` DISABLE KEYS */;
INSERT INTO `audio_analysis` VALUES (2,1,'default','v1','PENDING',NULL,NULL,'2026-02-06 16:14:00.369','2026-02-06 16:14:00.369');
/*!40000 ALTER TABLE `audio_analysis` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audio_file`
--

DROP TABLE IF EXISTS `audio_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audio_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `original_name` varchar(255) NOT NULL,
  `stored_name` varchar(255) NOT NULL,
  `storage_path` varchar(1024) NOT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `sha256` char(64) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'UPLOADED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audio_file_stored_name` (`stored_name`),
  KEY `idx_audio_file_user_time` (`user_id`,`created_at`),
  KEY `idx_audio_file_created_at` (`created_at`),
  CONSTRAINT `fk_audio_file_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audio_file`
--

LOCK TABLES `audio_file` WRITE;
/*!40000 ALTER TABLE `audio_file` DISABLE KEYS */;
INSERT INTO `audio_file` VALUES (1,NULL,'周杰伦 - 青花瓷.mp3','8b9be8fb47ef4271affb35368a2b1ec4.mp3','C:\\Dev\\projects\\ai-emotion-backend\\uploads\\8b9be8fb47ef4271affb35368a2b1ec4.mp3','audio/mpeg',9673328,NULL,NULL,'DELETED','2026-02-06 15:49:34.753','2026-02-07 12:08:46.844'),(2,NULL,'周杰伦 - 青花瓷.mp3','ffed7051e28648d3976454de4b6459ef.mp3','C:\\Dev\\projects\\ai-emotion-backend\\uploads\\ffed7051e28648d3976454de4b6459ef.mp3','audio/mpeg',9673328,NULL,NULL,'UPLOADED','2026-02-06 15:55:02.203','2026-02-06 15:55:02.203');
/*!40000 ALTER TABLE `audio_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audio_segment`
--

DROP TABLE IF EXISTS `audio_segment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audio_segment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `analysis_id` bigint NOT NULL,
  `start_ms` bigint NOT NULL,
  `end_ms` bigint NOT NULL,
  `transcript` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_segment_analysis` (`analysis_id`),
  KEY `idx_segment_time` (`start_ms`,`end_ms`),
  CONSTRAINT `fk_segment_analysis` FOREIGN KEY (`analysis_id`) REFERENCES `audio_analysis` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audio_segment`
--

LOCK TABLES `audio_segment` WRITE;
/*!40000 ALTER TABLE `audio_segment` DISABLE KEYS */;
/*!40000 ALTER TABLE `audio_segment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audio_tag`
--

DROP TABLE IF EXISTS `audio_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audio_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `audio_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audio_tag` (`audio_id`,`tag_id`),
  KEY `fk_at_tag` (`tag_id`),
  CONSTRAINT `fk_at_audio` FOREIGN KEY (`audio_id`) REFERENCES `audio_file` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_at_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audio_tag`
--

LOCK TABLES `audio_tag` WRITE;
/*!40000 ALTER TABLE `audio_tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `audio_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_menu`
--

DROP TABLE IF EXISTS `auth_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `path` varchar(200) NOT NULL,
  `name` varchar(100) NOT NULL,
  `type` varchar(20) NOT NULL DEFAULT 'MENU',
  `sort_no` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_menu_path` (`path`),
  KEY `idx_auth_menu_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_menu`
--

LOCK TABLES `auth_menu` WRITE;
/*!40000 ALTER TABLE `auth_menu` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_role`
--

DROP TABLE IF EXISTS `auth_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `name` varchar(50) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_role`
--

LOCK TABLES `auth_role` WRITE;
/*!40000 ALTER TABLE `auth_role` DISABLE KEYS */;
INSERT INTO `auth_role` (`id`,`code`,`name`,`created_at`) VALUES
(1,'USER','普通用户',NOW(3)),
(2,'ADMIN','运营管理员',NOW(3));
/*!40000 ALTER TABLE `auth_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_role_menu`
--

DROP TABLE IF EXISTS `auth_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_role_menu` (`role_id`,`menu_id`),
  KEY `fk_arm_menu` (`menu_id`),
  CONSTRAINT `fk_arm_menu` FOREIGN KEY (`menu_id`) REFERENCES `auth_menu` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_arm_role` FOREIGN KEY (`role_id`) REFERENCES `auth_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_role_menu`
--

LOCK TABLES `auth_role_menu` WRITE;
/*!40000 ALTER TABLE `auth_role_menu` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_user`
--

DROP TABLE IF EXISTS `auth_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password_hash` varchar(100) NOT NULL,
  `nickname` varchar(50) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_user_username` (`username`),
  KEY `idx_auth_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_user`
--

LOCK TABLES `auth_user` WRITE;
/*!40000 ALTER TABLE `auth_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_user_role`
--

DROP TABLE IF EXISTS `auth_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_user_role` (`user_id`,`role_id`),
  KEY `fk_aur_role` (`role_id`),
  CONSTRAINT `fk_aur_role` FOREIGN KEY (`role_id`) REFERENCES `auth_role` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_aur_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_user_role`
--

LOCK TABLES `auth_user_role` WRITE;
/*!40000 ALTER TABLE `auth_user_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_user_role` ENABLE KEYS */;
UNLOCK TABLES;


--
-- Table structure for table `auth_session`
--

DROP TABLE IF EXISTS `auth_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `access_token` varchar(128) NOT NULL,
  `refresh_token` varchar(128) NOT NULL,
  `access_expire_at` datetime(3) NOT NULL,
  `refresh_expire_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_session_access_token` (`access_token`),
  UNIQUE KEY `uk_auth_session_refresh_token` (`refresh_token`),
  KEY `idx_auth_session_user_id` (`user_id`),
  KEY `idx_auth_session_access_expire` (`access_expire_at`),
  KEY `idx_auth_session_refresh_expire` (`refresh_expire_at`),
  CONSTRAINT `fk_auth_session_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_session`
--

LOCK TABLES `auth_session` WRITE;
/*!40000 ALTER TABLE `auth_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `consultation_order`
--

DROP TABLE IF EXISTS `consultation_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consultation_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `counselor_id` bigint NOT NULL,
  `appointment_at` datetime(3) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'CREATED',
  `note` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_co_user_time` (`user_id`,`appointment_at`),
  KEY `idx_co_counselor_time` (`counselor_id`,`appointment_at`),
  KEY `idx_co_status` (`status`,`appointment_at`),
  CONSTRAINT `fk_co_counselor` FOREIGN KEY (`counselor_id`) REFERENCES `counselor_info` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_co_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `consultation_order`
--

LOCK TABLES `consultation_order` WRITE;
/*!40000 ALTER TABLE `consultation_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `consultation_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `core_feedback`
--

DROP TABLE IF EXISTS `core_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `core_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `analysis_id` bigint DEFAULT NULL,
  `rating` int DEFAULT NULL,
  `content` varchar(2000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `fk_feedback_user` (`user_id`),
  KEY `idx_feedback_time` (`created_at`),
  KEY `idx_feedback_analysis` (`analysis_id`),
  CONSTRAINT `fk_feedback_analysis` FOREIGN KEY (`analysis_id`) REFERENCES `audio_analysis` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `core_feedback`
--

LOCK TABLES `core_feedback` WRITE;
/*!40000 ALTER TABLE `core_feedback` DISABLE KEYS */;
/*!40000 ALTER TABLE `core_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `core_report`
--

DROP TABLE IF EXISTS `core_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `core_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `analysis_id` bigint NOT NULL,
  `title` varchar(200) NOT NULL DEFAULT 'Emotion Report',
  `report_json` json NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_report_analysis` (`analysis_id`),
  KEY `idx_report_analysis` (`analysis_id`),
  CONSTRAINT `fk_report_analysis` FOREIGN KEY (`analysis_id`) REFERENCES `audio_analysis` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `core_report`
--

LOCK TABLES `core_report` WRITE;
/*!40000 ALTER TABLE `core_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `core_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `counselor_info`
--

DROP TABLE IF EXISTS `counselor_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counselor_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `title` varchar(100) DEFAULT NULL,
  `intro` varchar(2000) DEFAULT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_counselor_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `counselor_info`
--

LOCK TABLES `counselor_info` WRITE;
/*!40000 ALTER TABLE `counselor_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `counselor_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `emotion_label`
--

DROP TABLE IF EXISTS `emotion_label`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `emotion_label` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `name_zh` varchar(50) NOT NULL,
  `name_en` varchar(50) DEFAULT NULL,
  `scheme` varchar(20) NOT NULL DEFAULT 'A',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emotion_code_scheme` (`code`,`scheme`),
  KEY `idx_emotion_scheme` (`scheme`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `emotion_label`
--

LOCK TABLES `emotion_label` WRITE;
/*!40000 ALTER TABLE `emotion_label` DISABLE KEYS */;
INSERT INTO `emotion_label` VALUES (1,'HAPPY','开心',NULL,'A'),(2,'SAD','难过',NULL,'A'),(3,'ANGRY','生气',NULL,'A'),(4,'CALM','平静',NULL,'A');
/*!40000 ALTER TABLE `emotion_label` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_plan`
--

DROP TABLE IF EXISTS `intervention_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervention_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `content` mediumtext NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_plan_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_plan`
--

LOCK TABLES `intervention_plan` WRITE;
/*!40000 ALTER TABLE `intervention_plan` DISABLE KEYS */;
/*!40000 ALTER TABLE `intervention_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `knowledge_article`
--

DROP TABLE IF EXISTS `knowledge_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `summary` varchar(1000) DEFAULT NULL,
  `content` mediumtext NOT NULL,
  `author` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ka_category` (`category_id`,`created_at`),
  KEY `idx_ka_status` (`status`,`created_at`),
  CONSTRAINT `fk_ka_category` FOREIGN KEY (`category_id`) REFERENCES `knowledge_category` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `knowledge_article`
--

LOCK TABLES `knowledge_article` WRITE;
/*!40000 ALTER TABLE `knowledge_article` DISABLE KEYS */;
/*!40000 ALTER TABLE `knowledge_article` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `knowledge_category`
--

DROP TABLE IF EXISTS `knowledge_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kc_name` (`name`),
  KEY `idx_kc_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `knowledge_category`
--

LOCK TABLES `knowledge_category` WRITE;
/*!40000 ALTER TABLE `knowledge_category` DISABLE KEYS */;
/*!40000 ALTER TABLE `knowledge_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `segment_emotion`
--

DROP TABLE IF EXISTS `segment_emotion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `segment_emotion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `segment_id` bigint NOT NULL,
  `emotion_id` bigint NOT NULL,
  `score` decimal(6,5) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_segment_emotion` (`segment_id`,`emotion_id`),
  KEY `idx_se_emotion` (`emotion_id`,`score`),
  CONSTRAINT `fk_se_emotion` FOREIGN KEY (`emotion_id`) REFERENCES `emotion_label` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_se_segment` FOREIGN KEY (`segment_id`) REFERENCES `audio_segment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `segment_emotion`
--

LOCK TABLES `segment_emotion` WRITE;
/*!40000 ALTER TABLE `segment_emotion` DISABLE KEYS */;
/*!40000 ALTER TABLE `segment_emotion` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict`
--

DROP TABLE IF EXISTS `sys_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_type` varchar(50) NOT NULL,
  `dict_key` varchar(100) NOT NULL,
  `dict_value` varchar(200) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict` (`dict_type`,`dict_key`),
  KEY `idx_dict_type` (`dict_type`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict`
--

LOCK TABLES `sys_dict` WRITE;
/*!40000 ALTER TABLE `sys_dict` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_dict` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `success` tinyint(1) NOT NULL,
  `ip` varchar(50) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_sll_time` (`created_at`),
  KEY `idx_sll_username` (`username`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_log`
--

LOCK TABLES `sys_login_log` WRITE;
/*!40000 ALTER TABLE `sys_login_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_login_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `content` varchar(5000) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_notice_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notice`
--

LOCK TABLES `sys_notice` WRITE;
/*!40000 ALTER TABLE `sys_notice` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_operation_log`
--

DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `action` varchar(100) NOT NULL,
  `detail` varchar(2000) DEFAULT NULL,
  `ip` varchar(50) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `fk_sol_user` (`user_id`),
  KEY `idx_sol_time` (`created_at`),
  KEY `idx_sol_action` (`action`,`created_at`),
  CONSTRAINT `fk_sol_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_operation_log`
--

LOCK TABLES `sys_operation_log` WRITE;
/*!40000 ALTER TABLE `sys_operation_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tag`
--

DROP TABLE IF EXISTS `tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tag`
--

LOCK TABLES `tag` WRITE;
/*!40000 ALTER TABLE `tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_emotion_daily`
--

DROP TABLE IF EXISTS `user_emotion_daily`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_emotion_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `day_date` date NOT NULL,
  `summary_json` json NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_day` (`user_id`,`day_date`),
  KEY `idx_ued_day` (`day_date`),
  CONSTRAINT `fk_ued_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_emotion_daily`
--

LOCK TABLES `user_emotion_daily` WRITE;
/*!40000 ALTER TABLE `user_emotion_daily` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_emotion_daily` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_plan_log`
--

DROP TABLE IF EXISTS `user_plan_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_plan_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  `action` varchar(50) NOT NULL DEFAULT 'APPLY',
  `remark` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `fk_upl_plan` (`plan_id`),
  KEY `idx_upl_user_time` (`user_id`,`created_at`),
  CONSTRAINT `fk_upl_plan` FOREIGN KEY (`plan_id`) REFERENCES `intervention_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_upl_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_plan_log`
--

LOCK TABLES `user_plan_log` WRITE;
/*!40000 ALTER TABLE `user_plan_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_plan_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-02-08 10:44:58
