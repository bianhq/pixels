//
// Created by bian on 2020-11-01.
//

#ifndef STORAGE_MANAGER_ERROR_CODE_H
#define STORAGE_MANAGER_ERROR_CODE_H

#define STM_SUCCESS 0
#define ERROR_BASE 1000
#define ERROR_MQ_IS_EMPTY (ERROR_BASE + 1)
#define ERROR_MQ_IS_FULL (ERROR_BASE + 2)
#define ERROR_MQ_WRITER_IS_ROLLBACK (ERROR_BASE + 3)
#define ERROR_MQ_WRITER_IS_RUNNING (ERROR_BASE + 4)
#define ERROR_MQ_READER_IS_ROLLBACK (ERROR_BASE + 5)
#define ERROR_MQ_READER_IS_RUNNING (ERROR_BASE + 6)
#define ERROR_CATALOG_CONTAINER_EXISTS (ERROR_BASE + 7)
#define ERROR_CATALOG_CONTAINER_NOT_EXISTS (ERROR_BASE + 8)
#define ERROR_CATALOG_OBJECT_EXISTS (ERROR_BASE + 9)
#define ERROR_CATALOG_OBJECT_NOT_EXISTS (ERROR_BASE + 10)
#define ERROR_CATALOG_SLICE_NOT_EXISTS (ERROR_BASE + 11)
#define ERROR_CATALOG_CREATE_FILE_FAIL (ERROR_BASE + 12)
#define ERROR_CATALOG_DELETE_FILE_FAIL (ERROR_BASE + 13)
#define ERROR_CATALOG_IPC_IN_LOCAL_SERVICE (ERROR_BASE + 14)
#define ERROR_READER_RESPONSE_TYPE_UNMATCH (ERROR_BASE + 15)
#define ERROR_READER_INDEX_OUT_OF_BOUND (ERROR_BASE + 16)
#define ERROR_WRITER_RESPONSE_TYPE_UNMATCH (ERROR_BASE + 17)
#define ERROR_WRITER_INDEX_OUT_OF_BOUND (ERROR_BASE + 18)
#define ERROR_NOT_SUPPORTED (ERROR_BASE + 19)
#define ERROR_STATISTICS_RELATION_NOT_EXISTS (ERROR_BASE + 20)
#define ERROR_SEND_GRPC_REQUEST_FAILED (ERROR_BASE + 21)
#define ERROR_LOCK_TYPE_INVALID (ERROR_BASE + 22)
#define ERROR_ACQUIRE_READ_LOCK_FAILED (ERROR_BASE + 23)
#define ERROR_ACQUIRE_WRITE_LOCK_FAILED (ERROR_BASE + 24)
#define ERROR_RW_LEASE_TIMEOUT (ERROR_BASE + 25)
#define ERROR_CANNOT_MOVE_PINNED_OBJECTS (ERROR_BASE + 26)
#define ERROR_CAS_NOT_MATCH (ERROR_BASE + 27)
#define ERROR_GENERAL_RESPONSE_TYPE_UNMATCH (ERROR_BASE + 28)
#define ERROR_FAILED_TO_ALLOCATE_SPACE (ERROR_BASE + 29)
#define ERROR_CATALOG_OBJECT_GROUP_NOT_EXISTS (ERROR_BASE + 30)

#endif // STORAGE_MANAGER_ERROR_CODE_H
