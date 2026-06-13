package com.trainqueue.scheduler.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcePoolTest {

    @Test
    void placesWhileItFitsAndRejectsWhenFull() {
        ResourcePool pool = new ResourcePool(4000, 8192);

        // two jobs of 2000/4096 fit exactly
        assertThat(pool.fits(2000, 4096)).isTrue();
        pool.reserve(2000, 4096);
        assertThat(pool.fits(2000, 4096)).isTrue();
        pool.reserve(2000, 4096);

        // pool is now full
        assertThat(pool.fits(1, 1)).isFalse();
        assertThat(pool.availableCpuMillis()).isZero();
        assertThat(pool.availableMemMb()).isZero();
    }

    @Test
    void rejectsAJobThatDoesNotFitEvenWhenPartlyFree() {
        ResourcePool pool = new ResourcePool(4000, 8192);
        pool.reserve(3000, 4096);
        assertThat(pool.fits(2000, 4096)).isFalse(); // not enough cpu
        assertThat(pool.fits(1000, 4096)).isTrue();
    }

    @Test
    void releaseFreesCapacityForTheNextJob() {
        ResourcePool pool = new ResourcePool(4000, 8192);
        pool.reserve(4000, 8192);
        assertThat(pool.fits(1000, 1024)).isFalse();

        pool.release(4000, 8192);
        assertThat(pool.fits(4000, 8192)).isTrue();
        assertThat(pool.availableCpuMillis()).isEqualTo(4000);
        assertThat(pool.availableMemMb()).isEqualTo(8192);
    }

    @Test
    void releaseNeverExceedsTotal() {
        ResourcePool pool = new ResourcePool(4000, 8192);
        pool.release(1000, 1024); // spurious release
        assertThat(pool.availableCpuMillis()).isEqualTo(4000);
        assertThat(pool.availableMemMb()).isEqualTo(8192);
    }
}
