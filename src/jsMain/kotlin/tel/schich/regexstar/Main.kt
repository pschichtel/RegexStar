package tel.schich.regexstar

import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.*
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onKeyUpFunction
import org.w3c.dom.HTMLInputElement
import kotlinx.html.org.w3c.dom.events.Event

fun main() {
    println("Test!")

    document.body!!.append.div {
        h1 {
            +"Convert your Regex to Doublestar!"
        }
        p {
            input(type = InputType.text) {
                placeholder = "Regex"

                fun update(event: Event) {
                    (event.target as? HTMLInputElement)?.value?.let { regexValue ->
                        (document.getElementById("doubestar") as? HTMLInputElement)?.let { doublestar ->

                            doublestar.value = compileDoublestar(optimize(parseRegex(regexValue)))
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
            }
        }
    }
}