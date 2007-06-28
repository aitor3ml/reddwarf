/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides an implementation of a circular byte-buffer.
 */

#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "sgs_buffer.h"

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static size_t readable_len(const sgs_buffer buffer);
static size_t tailpos (const sgs_buffer buffer);
static size_t writable_len(const sgs_buffer buffer);

/*
 * (EXTERNAL) FUNCTION IMPLEMENTATIONS
 */

/*
 * sgs_buffer_capacity()
 */
size_t sgs_buffer_capacity(const sgs_buffer buffer) {
  return buffer->capacity;
}

/*
 * sgs_buffer_create()
 */
sgs_buffer sgs_buffer_create(size_t capacity) {
  sgs_buffer buffer;
  
  buffer = (sgs_buffer)malloc(sizeof(struct sgs_buffer));
  if (buffer == NULL) return NULL;
  
  buffer->buf = (uint8_t*)malloc(capacity);
  
  if (buffer->buf == NULL) {
    /** "roll back" allocation of memory for buffer struct itself. */
    free(buffer);
    return NULL;
  }
  
  buffer->capacity = capacity;
  buffer->position = 0;
  buffer->size = 0;
  
  return buffer;
}

/*
 * sgs_buffer_destroy()
 */
void sgs_buffer_destroy(sgs_buffer buffer) {
  free(buffer->buf);
  free(buffer);
}

/*
 * sgs_buffer_empty()
 */
void sgs_buffer_empty(sgs_buffer buffer) {
  buffer->position = 0;  /** not necessary, but convenient */
  buffer->size = 0;
}

/*
 * sgs_buffer_peek()
 */
int sgs_buffer_peek(const sgs_buffer buffer, uint8_t *data, size_t len) {
  size_t readable = readable_len(buffer);
  
  if (len > buffer->size) return -1;
  
  if (readable >= len) {
    memcpy(data, buffer->buf + buffer->position, len);
  } else {
    memcpy(data, buffer->buf + buffer->position, readable);
    memcpy(data + readable, buffer->buf, len - readable);
  }
  
  return 0;
}

/*
 * sgs_buffer_read()
 */
int sgs_buffer_read(sgs_buffer buffer, uint8_t *data, size_t len) {
  if (sgs_buffer_peek(buffer, data, len) == -1) return -1;
  
  buffer->position = (buffer->position + len) % buffer->capacity;
  buffer->size -= len;
  return 0;
}

/*
 * sgs_buffer_read_from_fd()
 */
int sgs_buffer_read_from_fd(sgs_buffer buffer, int fd) {
  size_t result, total = 0, writable = writable_len(buffer);
  
  while (writable > 0) {
    result = read(fd, buffer->buf + tailpos(buffer), writable);
    if (result == -1) return -1;  /* error */
    if (result == 0) return 0;    /* EOF */
    total += result;
    buffer->size += result;
    if (result != writable) return total;  /* partial read */
    writable = writable_len(buffer);
  }
  
  return total;  /* buffer is full */
}

/*
 * sgs_buffer_remaining_capacity()
 */
size_t sgs_buffer_remaining_capacity(const sgs_buffer buffer) {
  return buffer->capacity - buffer->size;
}

/*
 * sgs_buffer_size()
 */
size_t sgs_buffer_size(const sgs_buffer buffer) {
  return buffer->size;
}

/*
 * sgs_buffer_write()
 */
int sgs_buffer_write(sgs_buffer buffer, const uint8_t *data, size_t len) {
  size_t writable = writable_len(buffer);
  
  if (len > sgs_buffer_remaining_capacity(buffer)) {
    errno = ENOBUFS;
    return -1;
  }
  
  if (writable >= len) {
    memcpy(buffer->buf + tailpos(buffer), data, len);
  } else {
    memcpy(buffer->buf + tailpos(buffer), data, writable);
    memcpy(buffer->buf, data + writable, len - writable);
  }
  
  buffer->size += len;
  return 0;
}

/*
 * sgs_buffer_write_to_fd()
 */
int sgs_buffer_write_to_fd(sgs_buffer buffer, int fd) {
  size_t result, total = 0, readable = readable_len(buffer);
  
  while (readable > 0) {
    result = write(fd, buffer->buf + buffer->position, readable);
    if (result == -1) return -1;  /* error */
    total += result;
    buffer->position = (buffer->position + result) % buffer->capacity;
    buffer->size -= result;
    if (result != readable) return total;  /* partial write */
    readable = readable_len(buffer);
  }
  
  return total;  /* buffer is empty */
}

/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 */

/*
 * readable_len()
 */
static size_t readable_len(const sgs_buffer buffer) {
  if (tailpos(buffer) >= buffer->position) {
    /*
     * The stored data has not wrapped yet, so we can read until we read the
     * tail.
     */
    return buffer->size;
  } else {
    /** 
     * The stored data HAS wrapped, we can we read until we reach the end of
     * the memory block.
     */
    return buffer->capacity - buffer->position;
  }
}

/*
 * tailpos()
 */
static size_t tailpos(const sgs_buffer buffer) {
  return (buffer->position + buffer->size) % buffer->capacity;
}

/*
 * writable_len()
 */
static size_t writable_len(const sgs_buffer buffer) {
  size_t mytailpos = tailpos(buffer);
  
  if (mytailpos >= buffer->position) {
    /*
     * The stored data has not wrapped yet, so we can write until we reach the
     * end of the memory block.
     */
    return buffer->capacity - mytailpos;
  } else {
    /** The stored data HAS wrapped, so we can write until we reach the head. */
    return buffer->position - mytailpos;
  }
}
