#include "opus_wrapper.h"

int* opus_errno(void) {
    return &errno;
}
