/**
 * Emergency Contacts Management Screen
 * Add, edit, and manage emergency contacts for the inner circle
 */

import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
    Alert,
    Linking,
    Modal,
    Platform,
    ScrollView,
    StyleSheet,
    TextInput,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, neutral, primary, semantic } from '@/constants/Colors';
import { useTheme } from '@/context/ThemeContext';
import {
    Contact,
    createContact,
    deleteContact,
    getContacts,
    updateContact,
} from '@/database/contacts';

type ContactRole = 'primary_caregiver' | 'caregiver' | 'neighbor' | 'family' | 'friend' | 'other';

const ROLE_OPTIONS: { value: ContactRole; label: string; icon: string }[] = [
  { value: 'primary_caregiver', label: 'Primary Caregiver', icon: 'star.fill' },
  { value: 'caregiver', label: 'Caregiver', icon: 'heart.fill' },
  { value: 'family', label: 'Family', icon: 'person.2.fill' },
  { value: 'neighbor', label: 'Neighbor', icon: 'house.fill' },
  { value: 'friend', label: 'Friend', icon: 'person.fill' },
  { value: 'other', label: 'Other', icon: 'ellipsis' },
];

interface FormData {
  name: string;
  phone: string;
  relationship: string;
  role: ContactRole;
  address: string;
  notifyOnEmergency: boolean;
  shareMedicalInfo: boolean;
  notes: string;
}

const EMPTY_FORM: FormData = {
  name: '',
  phone: '',
  relationship: '',
  role: 'family',
  address: '',
  notifyOnEmergency: true,
  shareMedicalInfo: false,
  notes: '',
};

export default function ContactsScreen() {
  const { colorScheme } = useTheme();
  const isDark = colorScheme === 'dark';
  const theme = Colors[colorScheme];

  const [contacts, setContacts] = useState<Contact[]>([]);

  // Modal state
  const [modalVisible, setModalVisible] = useState(false);
  const [modalType, setModalType] = useState<'delete' | 'error' | 'validation' | null>(null);
  const [modalMessage, setModalMessage] = useState('');
  const [pendingDelete, setPendingDelete] = useState<Contact | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [editingContact, setEditingContact] = useState<Contact | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<FormData>(EMPTY_FORM);

  const loadContacts = useCallback(async () => {
    try {
      const data = await getContacts();
      setContacts(data);
    } catch (error) {
      console.error('Failed to load contacts:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadContacts();
  }, [loadContacts]);

  const showAlert = (type: 'error' | 'validation', message: string) => {
    if (Platform.OS === 'web') {
      setModalType(type);
      setModalMessage(message);
      setModalVisible(true);
    } else {
      Alert.alert(type === 'validation' ? 'Required' : 'Error', message);
    }
  };

  const handleSave = async () => {
    if (!formData.name.trim()) {
      showAlert('validation', 'Please enter a name for this contact.');
      return;
    }
    if (!formData.phone.trim()) {
      showAlert('validation', 'Please enter a phone number.');
      return;
    }

    try {
      if (editingContact?.id) {
        await updateContact(editingContact.id, {
          name: formData.name,
          phone: formData.phone,
          relationship: formData.relationship || undefined,
          role: formData.role,
          address: formData.address || undefined,
          notifyOnEmergency: formData.notifyOnEmergency,
          shareMedicalInfo: formData.shareMedicalInfo,
          notes: formData.notes || undefined,
        });
      } else {
        await createContact({
          name: formData.name,
          phone: formData.phone,
          relationship: formData.relationship || undefined,
          role: formData.role,
          address: formData.address || undefined,
          notifyOnEmergency: formData.notifyOnEmergency,
          shareMedicalInfo: formData.shareMedicalInfo,
          notes: formData.notes || undefined,
        });
      }

      await loadContacts();
      setShowForm(false);
      setEditingContact(null);
      setFormData(EMPTY_FORM);
    } catch (error) {
      console.error('Failed to save contact:', error);
      showAlert('error', 'Failed to save contact. Please try again.');
    }
  };

  const handleEdit = (contact: Contact) => {
    setEditingContact(contact);
    setFormData({
      name: contact.name,
      phone: contact.phone,
      relationship: contact.relationship || '',
      role: contact.role || 'other',
      address: contact.address || '',
      notifyOnEmergency: contact.notifyOnEmergency,
      shareMedicalInfo: contact.shareMedicalInfo,
      notes: contact.notes || '',
    });
    setShowForm(true);
  };

  const handleDelete = (contact: Contact) => {
    if (Platform.OS === 'web') {
      setPendingDelete(contact);
      setModalType('delete');
      setModalMessage(`Are you sure you want to remove ${contact.name}?`);
      setModalVisible(true);
    } else {
      Alert.alert('Delete Contact', `Are you sure you want to remove ${contact.name}?`, [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => confirmDelete(contact),
        },
      ]);
    }
  };

  const confirmDelete = async (contact: Contact) => {
    try {
      if (contact.id) {
        await deleteContact(contact.id);
        await loadContacts();
      }
    } catch (error) {
      console.error('Failed to delete contact:', error);
      showAlert('error', 'Failed to delete contact.');
    }
  };

  const handleModalAction = (action: 'confirm' | 'cancel') => {
    setModalVisible(false);
    if (action === 'confirm' && modalType === 'delete' && pendingDelete) {
      confirmDelete(pendingDelete);
    }
    setPendingDelete(null);
    setModalType(null);
    setModalMessage('');
  };

  const handleCall = (phone: string) => {
    Linking.openURL(`tel:${phone}`);
  };

  const handleAddNew = () => {
    setEditingContact(null);
    setFormData(EMPTY_FORM);
    setShowForm(true);
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingContact(null);
    setFormData(EMPTY_FORM);
  };

  const getRoleInfo = (role?: ContactRole) => {
    return ROLE_OPTIONS.find((r) => r.value === role) || ROLE_OPTIONS[5];
  };

  const renderContactCard = (contact: Contact) => {
    const roleInfo = getRoleInfo(contact.role);

    return (
      <View
        key={contact.id}
        style={[
          styles.contactCard,
          {
            backgroundColor: isDark ? neutral[800] : neutral[50],
            borderColor: isDark ? neutral[700] : neutral[200],
          },
        ]}
      >
        <View style={styles.contactHeader}>
          <View style={styles.contactInfo}>
            <ThemedText style={styles.contactName}>{contact.name}</ThemedText>
            <View style={styles.roleRow}>
              <IconSymbol
                name={roleInfo.icon as any}
                size={12}
                color={isDark ? neutral[400] : neutral[500]}
              />
              <ThemedText
                style={styles.contactRole}
                lightColor={neutral[600]}
                darkColor={neutral[400]}
              >
                {roleInfo.label}
              </ThemedText>
            </View>
          </View>

          <View style={styles.contactActions}>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: semantic.success }]}
              onPress={() => handleCall(contact.phone)}
            >
              <IconSymbol name="phone.fill" size={16} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: primary[600] }]}
              onPress={() => handleEdit(contact)}
            >
              <IconSymbol name="pencil" size={16} color="#fff" />
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.contactDetails}>
          <View style={styles.detailRow}>
            <IconSymbol name="phone" size={14} color={isDark ? neutral[500] : neutral[400]} />
            <ThemedText
              style={styles.detailText}
              lightColor={neutral[700]}
              darkColor={neutral[300]}
            >
              {contact.phone}
            </ThemedText>
          </View>

          {contact.relationship && (
            <View style={styles.detailRow}>
              <IconSymbol name="person" size={14} color={isDark ? neutral[500] : neutral[400]} />
              <ThemedText
                style={styles.detailText}
                lightColor={neutral[700]}
                darkColor={neutral[300]}
              >
                {contact.relationship}
              </ThemedText>
            </View>
          )}

          {contact.address && (
            <View style={styles.detailRow}>
              <IconSymbol name="location" size={14} color={isDark ? neutral[500] : neutral[400]} />
              <ThemedText
                style={styles.detailText}
                lightColor={neutral[700]}
                darkColor={neutral[300]}
                numberOfLines={2}
              >
                {contact.address}
              </ThemedText>
            </View>
          )}
        </View>

        <TouchableOpacity style={styles.deleteButton} onPress={() => handleDelete(contact)}>
          <ThemedText style={[styles.deleteText, { color: semantic.error }]}>Remove</ThemedText>
        </TouchableOpacity>
      </View>
    );
  };

  const renderForm = () => (
    <ScrollView style={styles.formContainer} showsVerticalScrollIndicator={false}>
      <ThemedText style={styles.formTitle}>
        {editingContact ? 'Edit Contact' : 'Add Emergency Contact'}
      </ThemedText>

      {/* Name */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Name *
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.name}
          onChangeText={(text) => setFormData({ ...formData, name: text })}
          placeholder="Contact name"
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
        />
      </View>

      {/* Phone */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Phone Number *
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.phone}
          onChangeText={(text) => setFormData({ ...formData, phone: text })}
          placeholder="(555) 123-4567"
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
          keyboardType="phone-pad"
          autoComplete="tel"
        />
      </View>

      {/* Relationship */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Relationship
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.relationship}
          onChangeText={(text) => setFormData({ ...formData, relationship: text })}
          placeholder="e.g., Daughter, Next-door neighbor"
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
        />
      </View>

      {/* Address */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Address
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            styles.textArea,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.address}
          onChangeText={(text) => setFormData({ ...formData, address: text })}
          placeholder="Street address, city, state"
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
          multiline
          numberOfLines={2}
        />
      </View>

      {/* Role */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Role
        </ThemedText>
        <View style={styles.roleGrid}>
          {ROLE_OPTIONS.map((option) => (
            <TouchableOpacity
              key={option.value}
              style={[
                styles.roleOption,
                {
                  backgroundColor:
                    formData.role === option.value
                      ? primary[600]
                      : isDark
                        ? neutral[800]
                        : neutral[100],
                  borderColor:
                    formData.role === option.value
                      ? primary[600]
                      : isDark
                        ? neutral[600]
                        : neutral[300],
                },
              ]}
              onPress={() => setFormData({ ...formData, role: option.value })}
            >
              <IconSymbol
                name={option.icon as any}
                size={14}
                color={formData.role === option.value ? '#fff' : neutral[500]}
              />
              <ThemedText
                style={[styles.roleOptionText, formData.role === option.value && { color: '#fff' }]}
                lightColor={neutral[700]}
                darkColor={neutral[300]}
              >
                {option.label}
              </ThemedText>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* Notify on Emergency */}
      <TouchableOpacity
        style={[
          styles.toggleField,
          {
            backgroundColor: isDark ? neutral[800] : neutral[50],
            borderColor: isDark ? neutral[600] : neutral[300],
          },
        ]}
        onPress={() => setFormData({ ...formData, notifyOnEmergency: !formData.notifyOnEmergency })}
      >
        <View style={styles.toggleInfo}>
          <ThemedText style={styles.toggleLabel}>Notify in Emergency</ThemedText>
          <ThemedText style={styles.toggleHint} lightColor={neutral[500]} darkColor={neutral[400]}>
            Include in emergency SMS alerts
          </ThemedText>
        </View>
        <View
          style={[
            styles.toggle,
            {
              backgroundColor: formData.notifyOnEmergency
                ? semantic.success
                : isDark
                  ? neutral[600]
                  : neutral[300],
            },
          ]}
        >
          <View
            style={[styles.toggleKnob, formData.notifyOnEmergency && styles.toggleKnobActive]}
          />
        </View>
      </TouchableOpacity>

      {/* Share Medical Info */}
      <TouchableOpacity
        style={[
          styles.toggleField,
          {
            backgroundColor: isDark ? neutral[800] : neutral[50],
            borderColor: isDark ? neutral[600] : neutral[300],
          },
        ]}
        onPress={() => setFormData({ ...formData, shareMedicalInfo: !formData.shareMedicalInfo })}
      >
        <View style={styles.toggleInfo}>
          <ThemedText style={styles.toggleLabel}>Share Medical Info</ThemedText>
          <ThemedText style={styles.toggleHint} lightColor={neutral[500]} darkColor={neutral[400]}>
            Include medical details when alerting
          </ThemedText>
        </View>
        <View
          style={[
            styles.toggle,
            {
              backgroundColor: formData.shareMedicalInfo
                ? semantic.success
                : isDark
                  ? neutral[600]
                  : neutral[300],
            },
          ]}
        >
          <View style={[styles.toggleKnob, formData.shareMedicalInfo && styles.toggleKnobActive]} />
        </View>
      </TouchableOpacity>

      {/* Notes */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Notes
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            styles.textArea,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.notes}
          onChangeText={(text) => setFormData({ ...formData, notes: text })}
          placeholder="Additional notes about this contact..."
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
          multiline
          numberOfLines={3}
        />
      </View>

      {/* Buttons */}
      <View style={styles.formButtons}>
        <TouchableOpacity
          style={[styles.cancelButton, { borderColor: neutral[400] }]}
          onPress={handleCancel}
        >
          <ThemedText
            style={styles.cancelButtonText}
            lightColor={neutral[700]}
            darkColor={neutral[300]}
          >
            Cancel
          </ThemedText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.saveButton, { backgroundColor: primary[600] }]}
          onPress={handleSave}
        >
          <ThemedText style={styles.saveButtonText}>{editingContact ? 'Update' : 'Add'}</ThemedText>
        </TouchableOpacity>
      </View>

      <View style={{ height: 40 }} />
    </ScrollView>
  );

  const renderContactList = () => (
    <ScrollView style={styles.listContainer} showsVerticalScrollIndicator={false}>
      {contacts.length === 0 ? (
        <View style={styles.emptyState}>
          <IconSymbol name="person.2" size={48} color={isDark ? neutral[600] : neutral[300]} />
          <ThemedText style={styles.emptyTitle} lightColor={neutral[700]} darkColor={neutral[300]}>
            No Emergency Contacts
          </ThemedText>
          <ThemedText style={styles.emptyText} lightColor={neutral[500]} darkColor={neutral[400]}>
            Add family members, neighbors, or friends who can help in an emergency.
          </ThemedText>
        </View>
      ) : (
        <>
          <View style={styles.listHeader}>
            <ThemedText style={styles.listCount} lightColor={neutral[600]} darkColor={neutral[400]}>
              {contacts.length} contact{contacts.length !== 1 ? 's' : ''}
            </ThemedText>
          </View>
          {contacts.map(renderContactCard)}
        </>
      )}

      <TouchableOpacity
        style={[styles.addButton, { backgroundColor: primary[600] }]}
        onPress={handleAddNew}
      >
        <IconSymbol name="plus" size={20} color="#fff" />
        <ThemedText style={styles.addButtonText}>Add Contact</ThemedText>
      </TouchableOpacity>

      <View style={{ height: 40 }} />
    </ScrollView>
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        {/* Header */}
        <View style={[styles.header, { borderBottomColor: isDark ? neutral[800] : neutral[200] }]}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.replace('/(tabs)')}>
            <IconSymbol name="chevron.left" size={24} color={primary[600]} />
          </TouchableOpacity>
          <ThemedText style={styles.headerTitle}>Emergency Contacts</ThemedText>
          <View style={styles.headerRight} />
        </View>

        {/* Content */}
        {isLoading ? (
          <View style={styles.loading}>
            <ThemedText>Loading...</ThemedText>
          </View>
        ) : showForm ? (
          renderForm()
        ) : (
          renderContactList()
        )}
      </SafeAreaView>

      {/* Alert Modal for Web */}
      <Modal
        animationType="fade"
        transparent
        visible={modalVisible}
        onRequestClose={() => handleModalAction('cancel')}>
        <View style={styles.modalOverlay}>
          <View style={[styles.modalContent, { backgroundColor: theme.card }]}>
            <ThemedText style={styles.modalTitle}>
              {modalType === 'delete'
                ? 'Delete Contact'
                : modalType === 'validation'
                  ? 'Required'
                  : 'Error'}
            </ThemedText>
            <ThemedText style={styles.modalText}>{modalMessage}</ThemedText>
            <View
              style={[
                styles.modalButtons,
                modalType === 'delete' ? {} : { justifyContent: 'center' },
              ]}>
              {modalType === 'delete' && (
                <TouchableOpacity
                  style={[styles.modalButton, styles.modalCancelButton, { borderColor: theme.border }]}
                  onPress={() => handleModalAction('cancel')}>
                  <ThemedText style={styles.modalButtonText}>Cancel</ThemedText>
                </TouchableOpacity>
              )}
              <TouchableOpacity
                style={[
                  styles.modalButton,
                  modalType === 'delete'
                    ? { backgroundColor: semantic.error }
                    : { backgroundColor: primary[500] },
                ]}
                onPress={() => handleModalAction('confirm')}>
                <ThemedText style={[styles.modalButtonText, { color: '#fff' }]}>
                  {modalType === 'delete' ? 'Delete' : 'OK'}
                </ThemedText>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  backButton: {
    padding: 8,
    marginLeft: -8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
  },
  headerRight: {
    width: 40,
  },
  loading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },

  // List styles
  listContainer: {
    flex: 1,
    padding: 16,
  },
  listHeader: {
    marginBottom: 12,
  },
  listCount: {
    fontSize: 14,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 60,
    paddingHorizontal: 32,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginTop: 16,
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },

  // Contact card
  contactCard: {
    borderRadius: 12,
    borderWidth: 1,
    padding: 16,
    marginBottom: 12,
  },
  contactHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  contactInfo: {
    flex: 1,
  },
  contactName: {
    fontSize: 17,
    fontWeight: '600',
  },
  roleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginTop: 2,
  },
  contactRole: {
    fontSize: 13,
  },
  contactActions: {
    flexDirection: 'row',
    gap: 8,
  },
  actionButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
  },
  contactDetails: {
    gap: 6,
  },
  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  detailText: {
    fontSize: 14,
    flex: 1,
  },
  deleteButton: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: 'rgba(128,128,128,0.2)',
    alignItems: 'center',
  },
  deleteText: {
    fontSize: 14,
    fontWeight: '500',
  },
  addButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 16,
    borderRadius: 12,
    marginTop: 8,
  },
  addButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },

  // Form styles
  formContainer: {
    flex: 1,
    padding: 16,
  },
  formTitle: {
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 20,
  },
  formField: {
    marginBottom: 16,
  },
  fieldLabel: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 6,
  },
  textInput: {
    borderWidth: 1,
    borderRadius: 10,
    padding: 14,
    fontSize: 16,
  },
  textArea: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  roleGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  roleOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
  },
  roleOptionText: {
    fontSize: 13,
    fontWeight: '500',
  },
  toggleField: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 14,
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 16,
  },
  toggleInfo: {
    flex: 1,
    marginRight: 12,
  },
  toggleLabel: {
    fontSize: 15,
    fontWeight: '500',
  },
  toggleHint: {
    fontSize: 12,
    marginTop: 2,
  },
  toggle: {
    width: 50,
    height: 30,
    borderRadius: 15,
    padding: 3,
  },
  toggleKnob: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#fff',
  },
  toggleKnobActive: {
    transform: [{ translateX: 20 }],
  },
  formButtons: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 24,
  },
  cancelButton: {
    flex: 1,
    padding: 16,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
  },
  cancelButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
  saveButton: {
    flex: 1,
    padding: 16,
    borderRadius: 10,
    alignItems: 'center',
  },
  saveButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },

  // Modal styles
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalContent: {
    borderRadius: 16,
    padding: 24,
    width: '100%',
    maxWidth: 340,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 12,
    textAlign: 'center',
  },
  modalText: {
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 24,
    textAlign: 'center',
  },
  modalButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  modalButton: {
    flex: 1,
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: 10,
    alignItems: 'center',
  },
  modalCancelButton: {
    borderWidth: 1,
  },
  modalButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
});
