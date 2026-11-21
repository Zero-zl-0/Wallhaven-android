package com.wallhaven.browser.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 账号 API key 的全局入口：NSFW 的搜索和详情都必须带上它。
 * 由 AccountViewModel 写入（登录后从设置页读取），数据层直接读默认值即可。
 */
object ApiKeyStore {

    var key by mutableStateOf("")
        private set

    fun update(value: String) {
        if (key != value) key = value
    }
}
