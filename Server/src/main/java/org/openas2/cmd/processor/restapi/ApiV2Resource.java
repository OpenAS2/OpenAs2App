package org.openas2.cmd.processor.restapi;

import org.openas2.ComponentNotFoundException;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cert.CertificateFactory;
import org.openas2.cert.CertificateNotFoundException;
import org.openas2.cmd.processor.restapi.apiv2.Certificate;
import org.openas2.cmd.processor.restapi.apiv2.CertificateImport;
import org.openas2.cmd.processor.restapi.apiv2.ErrorObject;
import org.openas2.params.InvalidParameterException;
import org.openas2.partner.Partnership;
import org.openas2.partner.XMLPartnershipFactory;
import org.openas2.util.AS2Util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("api/v2/")
public class ApiV2Resource {
    public ApiV2Resource() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            mapper.setDateFormat(df);
        }
    }

    private static Session session;

    public static Session getSession() {
        return session;
    }

    public static void setSession(Session session) {
        ApiV2Resource.session = session;
    }

    private static XMLPartnershipFactory partnershipFactory;

    private static XMLPartnershipFactory getPartnershipFactory() {
        if (partnershipFactory == null) {
            try {
                partnershipFactory = (XMLPartnershipFactory) getSession().getPartnershipFactory();
            } catch (ComponentNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return partnershipFactory;
    }

    private static AliasedCertificateFactory certificateFactory;

    private static AliasedCertificateFactory getCertificateFactory() throws ComponentNotFoundException {
        if (certificateFactory == null) {
            CertificateFactory certFx = getSession().getCertificateFactory();

            if (certFx instanceof AliasedCertificateFactory) {
                certificateFactory = (AliasedCertificateFactory) certFx;
            }
        }

        return certificateFactory;
    }

    private static ObjectMapper mapper;

    private Response getEmptyOkResponse() {
        return Response.ok("{}").type(MediaType.APPLICATION_JSON).build();
    }

    private Response getOkResponse(Object object) throws JsonProcessingException {
        return Response.ok(mapper.writeValueAsString(object)).type(MediaType.APPLICATION_JSON).build();
    }

    private Response getErrorResponse(String errorMessage, Status status) throws JsonProcessingException {
        return ErrorObject.getResponse(errorMessage, status, mapper);
    }

    @RolesAllowed({ "ADMIN" })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/partners/")
    public Response getPartners() throws JsonProcessingException {
        Map<String, Object> partners = getPartnershipFactory().getPartners();

        List<Map<String, String>> partnerList = partners.values().stream().map(p -> (Map<String, String>) p)
                .collect(Collectors.toList());

        return getOkResponse(partnerList);
    }

    @RolesAllowed({ "ADMIN" })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/partners/{name}/")
    public Response getPartner(@PathParam("name") String name) throws JsonProcessingException {
        Map<String, String> partner = (Map<String, String>) getPartnershipFactory().getPartners().get(name);

        if (partner == null) {
            return getErrorResponse("Partner not found.", Status.NOT_FOUND);
        }

        return getOkResponse(partner);
    }

    @RolesAllowed({ "ADMIN" })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/certificates/")
    public Response getCertificates() throws JsonProcessingException, OpenAS2Exception {
        AliasedCertificateFactory certFx = getCertificateFactory();

        List<Certificate> certificates = new ArrayList<Certificate>();
        synchronized (certFx) {
            for (Map.Entry<String, X509Certificate> cert : certFx.getCertificates().entrySet()) {
                certificates.add(Certificate.fromX509Certificate(cert.getValue(), cert.getKey(),
                        certFx.hasPrivateKey(cert.getKey())));
            }
        }

        return getOkResponse(certificates);
    }

    @RolesAllowed({ "ADMIN" })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/certificates/{alias}/")
    public Response getCertificate(@PathParam("alias") String alias) throws JsonProcessingException, OpenAS2Exception {
        AliasedCertificateFactory certFx = getCertificateFactory();

        Certificate certificate;
        synchronized (certFx) {
            X509Certificate cert = certFx.getCertificates().get(alias);

            if (cert == null) {
                return Response.status(Status.NOT_FOUND).build();
            }

            certificate = Certificate.fromX509Certificate(cert, alias, certFx.hasPrivateKey(alias));
            certificate.setPublicKey(cert.getPublicKey().getEncoded());
        }

        return getOkResponse(certificate);
    }

    /**
     * Returns a certificate with the private key.
     * 
     * @param alias          The certificate alias.
     * @param exportPassword The export password for the private key.
     * @return The Certificate with the private key.
     * @throws Exception
     */
    @RolesAllowed({ "ADMIN" })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/certificates/{alias}/privatekey/{exportpassword}/")
    public Response getPrivateKey(@PathParam("alias") String alias, @PathParam("exportpassword") String exportPassword)
            throws Exception {
        AliasedCertificateFactory certFx = getCertificateFactory();

        Certificate certificate;
        synchronized (certFx) {
            X509Certificate cert = certFx.getCertificates().get(alias);

            if (cert == null || !certFx.hasPrivateKey(alias)) {
                return getErrorResponse("Certificate not found.", Status.NOT_FOUND);
            }

            PrivateKey key = certFx.getPrivateKey(alias);

            KeyStore ks = AS2Util.getCryptoHelper().getKeyStore();
            ks.load(null, null);
            ks.setKeyEntry(alias, key, exportPassword.toCharArray(), new java.security.cert.Certificate[] { cert });

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ks.store(os, exportPassword.toCharArray());

            certificate = Certificate.fromX509Certificate(cert, alias, true);
            certificate.setPublicKey(cert.getEncoded());
            certificate.setPkcs12Container(os.toByteArray());
        }

        return getOkResponse(certificate);
    }

    /**
     * Gets a list of all partners using a certificate.
     * 
     * @param alias The certificate alias.
     * @return A list of all partners using this certificate or status 404 (not
     *         found) if the alias is not known.
     * @throws JsonProcessingException
     * @throws OpenAS2Exception
     */
    @RolesAllowed({ "ADMIN" })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/certificates/{alias}/usedby/")
    public Response getCertificateUsingPartners(@PathParam("alias") String alias)
            throws JsonProcessingException, OpenAS2Exception {
        Map<String, Object> partners = getPartnershipFactory().getPartners();

        AliasedCertificateFactory certFx = getCertificateFactory();
        if (!certFx.getCertificates().containsKey(alias)) {
            return getErrorResponse("The certificate was not found", Status.NOT_FOUND);
        }

        List<Map<String, String>> partnerList = partners.values().stream()
                .map(p -> (Map<String, String>) p)
                .filter(p -> alias.equals(p.get(Partnership.PID_X509_ALIAS))
                        || alias.equals(p.get(Partnership.PID_X509_ALIAS_FALLBACK)))
                .collect(Collectors.toList());

        return getOkResponse(partnerList);
    }

    /**
     * Adds a new certificate to the certificate store. This can be a public
     * certificate or a certificate with a private key.
     * 
     * @param certificate The certificate import request
     * @return The Response with Status
     *         200 (Ok) it the import was successful,
     *         400 (Bad request) if the certificate request is not valid,
     *         409 (Conflict) if there is already a certificate with that alias or
     *         404 (Not found) if there was no certificate found in the data.
     * @throws Exception
     */
    @RolesAllowed({ "ADMIN" })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/certificates/")
    public Response addCertificate(CertificateImport certificate) throws Exception {
        if (certificate == null || certificate.getAlias() == null) {
            return getErrorResponse(CertificateImport.certificateImportUsage, Status.BAD_REQUEST);
        }

        boolean isPrivateKeyImport;
        try {
            isPrivateKeyImport = certificate.isPrivateKeyRequest();
        } catch (InvalidParameterException e) {
            return getErrorResponse(e.getMessage(), Status.BAD_REQUEST);
        }

        AliasedCertificateFactory certFx = getCertificateFactory();

        synchronized (certFx) {
            if (certFx.getCertificates().get(certificate.getAlias()) != null) {
                return getErrorResponse("A certificate with that name exists.", Status.CONFLICT);
            }

            try {
                if (isPrivateKeyImport) {
                    if (importPrivateKey(certFx, certificate, false)) {
                        // Return the inserted certificate
                        return getCertificate(certificate.getAlias());
                    }
                } else {
                    if (importPublicCert(certFx, certificate, false)) {
                        // Return the inserted certificate
                        return getCertificate(certificate.getAlias());
                    }
                }

                return getErrorResponse("No certificate found", Status.NOT_FOUND);
            } catch (Exception e) {
                return getErrorResponse("Error importing certificate: " + e.getMessage(), Status.BAD_REQUEST);
            }
        }
    }

    /**
     * Replaces a certificate in the certificate store. This can be a public
     * certificate or a certificate with a private key.
     * 
     * @param certificate The certificate import request
     * @return The Response with Status
     *         200 (Ok) it the import was successful,
     *         400 (Bad request) if the certificate request is not valid or
     *         404 (Not found) if there was no certificate found in the data or
     *         there was no certificate with the alias in the key store.
     * @throws Exception
     */
    @RolesAllowed({ "ADMIN" })
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/certificates/{alias}/")
    public Response replaceCertificate(@PathParam("alias") String alias, CertificateImport certificate)
            throws Exception {
        if (certificate == null || certificate.getAlias() == null) {
            return getErrorResponse(CertificateImport.certificateImportUsage, Status.BAD_REQUEST);
        }

        if (!alias.equals(certificate.getAlias())) {
            return getErrorResponse("The path alias doesn't match the body alias.", Status.BAD_REQUEST);
        }

        boolean isPrivateKeyImport;
        try {
            isPrivateKeyImport = certificate.isPrivateKeyRequest();
        } catch (InvalidParameterException e) {
            return ErrorObject.getResponse(e.getMessage(), Status.BAD_REQUEST, mapper);
        }

        AliasedCertificateFactory certFx = getCertificateFactory();

        synchronized (certFx) {
            if (certFx.getCertificates().get(certificate.getAlias()) == null) {
                return ErrorObject.getResponse("There was no certificate with the alias found.", Status.NOT_FOUND,
                        mapper);
            }

            try {
                if (isPrivateKeyImport) {
                    if (importPrivateKey(certFx, certificate, true)) {
                        // Return the inserted certificate
                        return getCertificate(certificate.getAlias());
                    }
                } else {
                    if (importPublicCert(certFx, certificate, true)) {
                        // Return the inserted certificate
                        return getCertificate(certificate.getAlias());
                    }
                }

                return ErrorObject.getResponse("No certificate found in data.", Status.NOT_FOUND, mapper);
            } catch (Exception e) {
                return ErrorObject.getResponse("Error importing certificate: " + e.getMessage(), Status.BAD_REQUEST,
                        mapper);
            }
        }
    }

    @RolesAllowed({ "ADMIN" })
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/certificates/{alias}/")
    public Response deleteCertificate(@PathParam("alias") String alias)
            throws OpenAS2Exception, JsonProcessingException {
        AliasedCertificateFactory certFx = getCertificateFactory();

        synchronized (certFx) {
            try {
                certFx.removeCertificate(alias);
            } catch (CertificateNotFoundException e) {
                return ErrorObject.getResponse("certificate was not found", Status.NOT_FOUND, mapper);
            }
        }

        return getEmptyOkResponse();
    }

    /**
     * Import a public certificate.
     * 
     * @param certFx     The certificate factory
     * @param certImport The certificate import request with alias and public key.
     * @param replace    true if an existing certificate should be replaced,
     *                   otherwise false.
     * @return True if a certificate was found in the certificate import, otherwise
     *         false.
     * @throws CertificateException There was an error loading the certificate.
     * @throws OpenAS2Exception     There was an error adding the certificate.
     */
    private boolean importPublicCert(AliasedCertificateFactory certFx, CertificateImport certImport, boolean replace)
            throws CertificateException, OpenAS2Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(certImport.getPublicKey());

        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");

        while (bais.available() > 0) {
            java.security.cert.Certificate cert = cf.generateCertificate(bais);

            if (cert instanceof X509Certificate) {
                certFx.addCertificate(certImport.getAlias(), (X509Certificate) cert, replace);

                return true;
            }
        }

        return false;
    }

    /**
     * Import a private key.
     * 
     * @param certFx     The certificate factory.
     * @param certImport Teh certificate import request with alias, pkcs12Container
     *                   and password.
     * @param replace    true if an existing certificate should be replaced,
     *                   otherwise false.
     * @return True if a certificate was found in the certificate import, otherwise
     *         false.
     * @throws Exception
     */
    private boolean importPrivateKey(AliasedCertificateFactory certFx, CertificateImport certImport, boolean replace)
            throws Exception {
        KeyStore ks = AS2Util.getCryptoHelper().getKeyStore();
        ks.load(new ByteArrayInputStream(certImport.getPkcs12Container()), certImport.getPassword().toCharArray());

        Enumeration<String> aliases = ks.aliases();

        while (aliases.hasMoreElements()) {
            String certAlias = aliases.nextElement();
            java.security.cert.Certificate cert = ks.getCertificate(certAlias);

            if (cert instanceof X509Certificate) {
                Key certKey = ks.getKey(certAlias, certImport.getPassword().toCharArray());

                if (certKey == null) {
                    throw new Exception("Corresponding private key not found.");
                }

                if (replace) {
                    certFx.removeCertificate(certImport.getAlias());
                }

                certFx.addCertificate(certImport.getAlias(), (X509Certificate) cert, replace);
                certFx.addPrivateKey(certImport.getAlias(), certKey, certImport.getPassword());

                return true;
            }
        }

        return false;
    }
}
