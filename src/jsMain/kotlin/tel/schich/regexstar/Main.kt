package tel.schich.regexstar

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.*
import kotlinx.html.dom.*
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onKeyUpFunction
import org.w3c.dom.HTMLInputElement
import kotlinx.html.org.w3c.dom.events.Event
import org.w3c.dom.url.URL

fun main() {
    fun currentUrl() = document.location?.href?.let(::URL)
    val urlRegex = currentUrl()?.searchParams?.get("regex")
    fun translate(s: String): String {
        val regex = parseRegex(s)
        console.log("Regex: $regex")
        val optimizedRegex = optimize(regex)
        console.log("Optimized: $optimizedRegex")
        return compileDoublestar(optimizedRegex, quantifierLimit = 4)
    }

    document.body!!.append.div {
        h1 {
            +"Convert your Regex to Doublestar!"
        }
        p {
            input(type = InputType.text) {
                placeholder = "Regex"
                if (urlRegex != null) {
                    value = urlRegex
                }

                fun update(event: Event) {
                    (event.target as? HTMLInputElement)?.value?.let { regexValue ->
                        currentUrl()
                            ?.also {
                                it.searchParams.set("regex", regexValue)
                            }
                            ?.let {
                                window.history.pushState(null, "", it.href)
                            }
                        (document.getElementById("doubestar") as? HTMLInputElement)?.let { doublestar ->
                            val pattern = translate(regexValue)
                            doublestar.value = pattern
                        }
                    }
                }

                onChangeFunction = ::update
                onKeyUpFunction = ::update
            }
        }
        p {
            input(type = InputType.text) {
                id = "doubestar"
                readonly = true
                placeholder = "Doublestar"
                if (urlRegex != null) {
                    value = translate(urlRegex)
                }
            }
        }
        p {
            a("https://github.com/pschichtel/RegexStar") {
                +"Pull Requests are welcome on Github.com!"
            }
        }
    }
}