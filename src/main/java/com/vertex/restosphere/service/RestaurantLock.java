package com.vertex.restosphere.service;

/** Khoa toan nha hang o muc DB, tu nha khi transaction ket thuc. Chi goi ben trong transaction. */
public interface RestaurantLock {

    void acquire();
}
