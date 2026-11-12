package br.edu.ufape.sguPraeService;

import br.edu.ufape.sguPraeService.config.ModelMapperBeanConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SguPraeServiceApplicationTests {

    @Test
    @DisplayName("Deve instanciar configurações essenciais da aplicação sem depender de containers externos")
    void contextConfigurationsLoad() {
        ModelMapperBeanConfiguration config = new ModelMapperBeanConfiguration();
        ModelMapper mapper = config.modelMapper();
        assertNotNull(mapper);
    }
}
