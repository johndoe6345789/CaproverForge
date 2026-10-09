package com.caproverforge.testing

import com.caproverforge.data.TokenCodec

/** Robolectric has no AndroidKeyStore; tests store the token as-is. */
object PlainTokenCodec : TokenCodec {
    override fun encrypt(plain: String) = plain
    override fun decrypt(encoded: String) = encoded
}
