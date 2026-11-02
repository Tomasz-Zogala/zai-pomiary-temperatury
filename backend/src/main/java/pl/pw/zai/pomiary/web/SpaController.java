package pl.pw.zai.pomiary.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Odświeżenie strony pod adresem trasy Angulara zwraca aplikację zamiast 404
@Controller
public class SpaController {

    @GetMapping({"/login", "/admin", "/admin/{*path}"})
    public String index() {
        return "forward:/index.html";
    }
}
