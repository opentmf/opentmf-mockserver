package org.mockserver.serialization.serializers.certificate;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.mockserver.model.X509Certificate;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class X509CertificateSerializer extends StdSerializer<X509Certificate> {

    public X509CertificateSerializer() {
        super(X509Certificate.class);
    }

    @Override
    public void serialize(X509Certificate x509Certificate, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (isNotBlank(x509Certificate.getSerialNumber())) {
            jgen.writePOJOProperty("serialNumber", x509Certificate.getSerialNumber());
        }
        if (isNotBlank(x509Certificate.getIssuerDistinguishedName())) {
            jgen.writePOJOProperty("issuerDistinguishedName", x509Certificate.getIssuerDistinguishedName());
        }
        if (isNotBlank(x509Certificate.getSubjectDistinguishedName())) {
            jgen.writePOJOProperty("subjectDistinguishedName", x509Certificate.getSubjectDistinguishedName());
        }
        if (isNotBlank(x509Certificate.getSignatureAlgorithmName())) {
            jgen.writePOJOProperty("signatureAlgorithmName", x509Certificate.getSignatureAlgorithmName());
        }
        jgen.writeEndObject();
    }
}
